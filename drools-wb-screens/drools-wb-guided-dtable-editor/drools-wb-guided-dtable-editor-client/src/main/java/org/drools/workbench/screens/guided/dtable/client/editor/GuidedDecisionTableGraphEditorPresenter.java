/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.drools.workbench.screens.guided.dtable.client.editor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.PostConstruct;
import javax.enterprise.context.Dependent;
import javax.enterprise.event.Event;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.user.client.ui.IsWidget;
import org.drools.workbench.models.datamodel.imports.Imports;
import org.drools.workbench.models.guided.dtable.shared.model.GuidedDecisionTable52;
import org.drools.workbench.screens.guided.dtable.client.editor.menu.EditMenuBuilder;
import org.drools.workbench.screens.guided.dtable.client.editor.menu.InsertMenuBuilder;
import org.drools.workbench.screens.guided.dtable.client.editor.menu.RadarMenuBuilder;
import org.drools.workbench.screens.guided.dtable.client.editor.menu.ViewMenuBuilder;
import org.drools.workbench.screens.guided.dtable.client.type.GuidedDTableGraphResourceType;
import org.drools.workbench.screens.guided.dtable.client.widget.table.GuidedDecisionTableModellerView;
import org.drools.workbench.screens.guided.dtable.client.widget.table.GuidedDecisionTablePresenter;
import org.drools.workbench.screens.guided.dtable.client.widget.table.GuidedDecisionTablePresenter.Access;
import org.drools.workbench.screens.guided.dtable.client.widget.table.GuidedDecisionTableView;
import org.drools.workbench.screens.guided.dtable.client.widget.table.events.cdi.DecisionTableSelectedEvent;
import org.drools.workbench.screens.guided.dtable.client.wizard.NewGuidedDecisionTableWizardHelper;
import org.drools.workbench.screens.guided.dtable.model.GuidedDecisionTableEditorContent;
import org.drools.workbench.screens.guided.dtable.model.GuidedDecisionTableEditorGraphContent;
import org.drools.workbench.screens.guided.dtable.model.GuidedDecisionTableEditorGraphModel;
import org.drools.workbench.screens.guided.dtable.service.GuidedDecisionTableEditorService;
import org.drools.workbench.screens.guided.dtable.service.GuidedDecisionTableGraphEditorService;
import org.guvnor.common.services.project.context.ProjectContext;
import org.guvnor.common.services.shared.metadata.model.Metadata;
import org.guvnor.common.services.shared.metadata.model.Overview;
import org.jboss.errai.bus.client.api.messaging.Message;
import org.jboss.errai.common.client.api.Caller;
import org.jboss.errai.common.client.api.RemoteCallback;
import org.jboss.errai.ioc.client.container.SyncBeanManager;
import org.kie.workbench.common.widgets.client.callbacks.CommandDrivenErrorCallback;
import org.kie.workbench.common.widgets.client.datamodel.AsyncPackageDataModelOracle;
import org.uberfire.backend.vfs.ObservablePath;
import org.uberfire.backend.vfs.Path;
import org.uberfire.client.annotations.WorkbenchEditor;
import org.uberfire.client.annotations.WorkbenchMenu;
import org.uberfire.client.annotations.WorkbenchPartTitle;
import org.uberfire.client.annotations.WorkbenchPartView;
import org.uberfire.client.callbacks.Callback;
import org.uberfire.client.mvp.LockManager;
import org.uberfire.client.mvp.LockTarget;
import org.uberfire.client.mvp.PlaceManager;
import org.uberfire.client.mvp.SaveInProgressEvent;
import org.uberfire.client.mvp.UpdatedLockStatusEvent;
import org.uberfire.client.workbench.events.ChangeTitleWidgetEvent;
import org.uberfire.ext.editor.commons.client.menu.MenuItems;
import org.uberfire.ext.editor.commons.client.resources.i18n.CommonConstants;
import org.uberfire.ext.editor.commons.version.events.RestoreEvent;
import org.uberfire.ext.widgets.common.client.callbacks.DefaultErrorCallback;
import org.uberfire.ext.widgets.common.client.callbacks.HasBusyIndicatorDefaultErrorCallback;
import org.uberfire.lifecycle.OnClose;
import org.uberfire.lifecycle.OnFocus;
import org.uberfire.lifecycle.OnMayClose;
import org.uberfire.lifecycle.OnStartup;
import org.uberfire.mvp.Command;
import org.uberfire.mvp.ParameterizedCommand;
import org.uberfire.mvp.PlaceRequest;
import org.uberfire.mvp.impl.PathPlaceRequest;
import org.uberfire.workbench.events.NotificationEvent;
import org.uberfire.workbench.model.menu.Menus;

import static org.drools.workbench.screens.guided.dtable.client.widget.table.GuidedDecisionTablePresenter.Access.LockedBy.*;
import static org.uberfire.client.annotations.WorkbenchEditor.LockingStrategy.*;
import static org.uberfire.ext.widgets.common.client.common.ConcurrentChangePopup.*;

/**
 * Guided Decision Table Graph Editor Presenter
 */
@Dependent
@WorkbenchEditor(identifier = "GuidedDecisionTableGraphEditor", supportedTypes = { GuidedDTableGraphResourceType.class }, lockingStrategy = EDITOR_PROVIDED)
public class GuidedDecisionTableGraphEditorPresenter extends BaseGuidedDecisionTableEditorPresenter {

    private final Caller<GuidedDecisionTableGraphEditorService> graphService;
    private final Event<SaveInProgressEvent> saveInProgressEvent;
    private final LockManager lockManager;

    private GuidedDecisionTableEditorGraphContent content;
    private LoadGraphLatch loadGraphLatch = null;
    private SaveGraphLatch saveGraphLatch = null;

    private ProjectContext context;
    private NewGuidedDecisionTableWizardHelper helper;

    protected ObservablePath.OnConcurrentUpdateEvent concurrentUpdateSessionInfo = null;
    protected Access access = new Access();
    protected Integer originalGraphHash;

    private class LoadGraphLatch {

        private int dtGraphElementCount;
        private Command onAllDocumentGraphEntriesLoadedCommand;
        private ParameterizedCommand<GuidedDecisionTableView.Presenter> onDocumentGraphEntryLoadedCommand;

        private LoadGraphLatch( final int dtGraphElementCount,
                                final ParameterizedCommand<GuidedDecisionTableView.Presenter> onDocumentGraphEntryLoadedCommand ) {
            this( dtGraphElementCount,
                  onDocumentGraphEntryLoadedCommand,
                  () -> {/*Do nothing*/} );
        }

        private LoadGraphLatch( final int dtGraphElementCount,
                                final ParameterizedCommand<GuidedDecisionTableView.Presenter> onDocumentGraphEntryLoadedCommand,
                                final Command onAllDocumentGraphEntriesLoadedCommand ) {
            this.dtGraphElementCount = dtGraphElementCount;
            this.onDocumentGraphEntryLoadedCommand = onDocumentGraphEntryLoadedCommand;
            this.onAllDocumentGraphEntriesLoadedCommand = onAllDocumentGraphEntriesLoadedCommand;
        }

        private void onDocumentGraphEntryLoaded( final GuidedDecisionTableView.Presenter dtPresenter ) {
            if ( onDocumentGraphEntryLoadedCommand != null ) {
                onDocumentGraphEntryLoadedCommand.execute( dtPresenter );
            }
        }

        private void hideLoadingIndicator() {
            dtGraphElementCount--;
            if ( dtGraphElementCount == 0 ) {
                if ( onAllDocumentGraphEntriesLoadedCommand != null ) {
                    onAllDocumentGraphEntriesLoadedCommand.execute();
                }
                view.hideBusyIndicator();
            }
        }

        private void loadDocumentGraphEntry( final GuidedDecisionTableEditorGraphModel.GuidedDecisionTableGraphEntry entry ) {
            final PathPlaceRequest placeRequest = getPathPlaceRequest( entry.getPathHead() );
            final ObservablePath pathHead = placeRequest.getPath();
            final Path pathVersion = entry.getPathVersion();
            final Double x = entry.getX();
            final Double y = entry.getY();

            if ( isReadOnly() ) {
                placeRequest.addParameter( "readOnly", "" );
            }

            service.call( getLoadDocumentGraphEntryContentSuccessCallback( pathHead,
                                                                           placeRequest,
                                                                           x,
                                                                           y ),
                          getLoadErrorCallback() ).loadContent( pathVersion );
        }

        private RemoteCallback<GuidedDecisionTableEditorContent> getLoadDocumentGraphEntryContentSuccessCallback( final ObservablePath path,
                                                                                                                  final PlaceRequest placeRequest,
                                                                                                                  final Double x,
                                                                                                                  final Double y ) {
            return ( content ) -> {
                //Path is set to null when the Editor is closed (which can happen before async calls complete).
                if ( path == null ) {
                    return;
                }

                //Add Decision Table to modeller
                final GuidedDecisionTableView.Presenter dtPresenter = modeller.addDecisionTable( path,
                                                                                                 placeRequest,
                                                                                                 content,
                                                                                                 placeRequest.getParameter( "readOnly", null ) != null,
                                                                                                 x,
                                                                                                 y );
                registerDocument( dtPresenter );

                onDocumentGraphEntryLoaded( dtPresenter );

                hideLoadingIndicator();
            };
        }

        private void loadDocument( final ObservablePath path,
                                   final PlaceRequest placeRequest ) {
            service.call( getLoadContentSuccessCallback( path,
                                                         placeRequest ),
                          getLoadErrorCallback() ).loadContent( path );
        }

        private RemoteCallback<GuidedDecisionTableEditorContent> getLoadContentSuccessCallback( final ObservablePath path,
                                                                                                final PlaceRequest placeRequest ) {
            return ( content ) -> {
                //Path is set to null when the Editor is closed (which can happen before async calls complete).
                if ( path == null ) {
                    return;
                }

                //Add Decision Table to modeller
                final GuidedDecisionTableView.Presenter dtPresenter = modeller.addDecisionTable( path,
                                                                                                 placeRequest,
                                                                                                 content,
                                                                                                 placeRequest.getParameter( "readOnly", null ) != null,
                                                                                                 null,
                                                                                                 null );
                registerDocument( dtPresenter );

                onDocumentGraphEntryLoaded( dtPresenter );

                hideLoadingIndicator();
            };
        }

        private DefaultErrorCallback getLoadErrorCallback() {
            final CommandDrivenErrorCallback wrapped = getNoSuchFileExceptionErrorCallback();
            final DefaultErrorCallback callback = new DefaultErrorCallback() {
                @Override
                public boolean error( final Message message,
                                      final Throwable throwable ) {
                    hideLoadingIndicator();
                    return wrapped.error( message,
                                          throwable );
                }
            };
            return callback;
        }

    }

    private class SaveGraphLatch {

        private int dtGraphElementCount = 0;
        private final String commitMessage;

        private SaveGraphLatch( final int dtGraphElementCount,
                                final String commitMessage ) {
            this.dtGraphElementCount = dtGraphElementCount;
            this.commitMessage = commitMessage;
        }

        private void saveDocumentGraph() {
            dtGraphElementCount--;
            if ( dtGraphElementCount > 0 ) {
                return;
            }

            final GuidedDecisionTableEditorGraphModel model = buildModelFromEditor();
            graphService.call( new RemoteCallback<Path>() {
                                   @Override
                                   public void callback( final Path path ) {
                                       editorView.hideBusyIndicator();
                                       versionRecordManager.reloadVersions( path );
                                       originalGraphHash = model.hashCode();
                                       concurrentUpdateSessionInfo = null;
                                       notificationEvent.fire( new NotificationEvent( CommonConstants.INSTANCE.ItemSavedSuccessfully() ) );
                                   }
                               },
                               new HasBusyIndicatorDefaultErrorCallback( view ) ).save( editorPath,
                                                                                        model,
                                                                                        content.getOverview().getMetadata(),
                                                                                        commitMessage );
        }

        private void saveDocumentGraphEntry( final GuidedDecisionTableView.Presenter dtPresenter ) {
            final ObservablePath path = dtPresenter.getCurrentPath();
            final GuidedDecisionTable52 model = dtPresenter.getModel();
            final Metadata metadata = dtPresenter.getOverview().getMetadata();

            service.call( getSaveSuccessCallback( dtPresenter,
                                                  model.hashCode() ),
                          getSaveErrorCallback() ).save( path,
                                                         model,
                                                         metadata,
                                                         commitMessage );
        }

        private RemoteCallback<Path> getSaveSuccessCallback( final GuidedDecisionTableView.Presenter document,
                                                             final int currentHashCode ) {
            return ( path ) -> {
                document.setConcurrentUpdateSessionInfo( null );
                document.setOriginalHashCode( currentHashCode );
                saveDocumentGraph();
            };
        }

        private DefaultErrorCallback getSaveErrorCallback() {
            return new HasBusyIndicatorDefaultErrorCallback( view ) {
                @Override
                public boolean error( final Message message,
                                      final Throwable throwable ) {
                    saveDocumentGraph();
                    return super.error( message,
                                        throwable );
                }
            };
        }

    }

    @Inject
    public GuidedDecisionTableGraphEditorPresenter( final View view,
                                                    final Caller<GuidedDecisionTableEditorService> service,
                                                    final Caller<GuidedDecisionTableGraphEditorService> graphService,
                                                    final Event<NotificationEvent> notification,
                                                    final Event<SaveInProgressEvent> saveInProgressEvent,
                                                    final Event<DecisionTableSelectedEvent> decisionTableSelectedEvent,
                                                    final GuidedDTableGraphResourceType dtGraphResourceType,
                                                    final EditMenuBuilder editMenuBuilder,
                                                    final ViewMenuBuilder viewMenuBuilder,
                                                    final InsertMenuBuilder insertMenuBuilder,
                                                    final RadarMenuBuilder radarMenuBuilder,
                                                    final GuidedDecisionTableModellerView.Presenter modeller,
                                                    final ProjectContext context,
                                                    final NewGuidedDecisionTableWizardHelper helper,
                                                    final SyncBeanManager beanManager,
                                                    final PlaceManager placeManager,
                                                    final LockManager lockManager ) {
        super( view,
               service,
               notification,
               decisionTableSelectedEvent,
               dtGraphResourceType,
               editMenuBuilder,
               viewMenuBuilder,
               insertMenuBuilder,
               radarMenuBuilder,
               modeller,
               beanManager,
               placeManager );
        this.graphService = graphService;
        this.saveInProgressEvent = saveInProgressEvent;
        this.context = context;
        this.helper = helper;
        this.lockManager = lockManager;
    }

    @PostConstruct
    public void init() {
        super.init();

        //Selecting a Decision Table in the document selector fires a selection event
        registeredDocumentsMenuBuilder.setActivateDocumentCommand( ( document ) -> {
            final GuidedDecisionTablePresenter dtPresenter = ( (GuidedDecisionTablePresenter) document );
            decisionTableSelectedEvent.fire( new DecisionTableSelectedEvent( dtPresenter ) );
        } );

        //Removing a Decision Table from the document selector is equivalent to closing the editor
        registeredDocumentsMenuBuilder.setRemoveDocumentCommand( ( document ) -> {
            final GuidedDecisionTablePresenter dtPresenter = ( (GuidedDecisionTablePresenter) document );
            if ( mayClose( dtPresenter ) ) {
                removeDocument( dtPresenter );
            }
        } );

        registeredDocumentsMenuBuilder.setNewDocumentCommand( this::onNewDocument );
    }

    void onNewDocument() {
        final Path contextPath = context.getActivePackage().getPackageMainResourcesPath();
        helper.createNewGuidedDecisionTable( contextPath,
                                             "",
                                             GuidedDecisionTable52.TableFormat.EXTENDED_ENTRY,
                                             view,
                                             ( path ) -> onOpenDocumentsInEditor( Collections.singletonList( path ) ) );
    }

    @Override
    @OnStartup
    public void onStartup( final ObservablePath path,
                           final PlaceRequest placeRequest ) {
        super.onStartup( path,
                         placeRequest );

        initialiseEditor( path,
                          placeRequest );
    }

    @Override
    @OnFocus
    public void onFocus() {
        super.onFocus();
    }

    @Override
    public void loadDocument( final ObservablePath path,
                              final PlaceRequest placeRequest ) {
        throw new UnsupportedOperationException();
    }

    void initialiseEditor( final ObservablePath path,
                           final PlaceRequest placeRequest ) {
        this.access.setReadOnly( placeRequest.getParameter( "readOnly", null ) != null );

        initialiseLockManager();
        initialiseVersionManager();
        addFileChangeListeners( path );

        loadDocumentGraph( path );
    }

    void initialiseVersionManager() {
        versionRecordManager.init( null,
                                   editorPath,
                                   ( versionRecord ) -> {
                                       versionRecordManager.setVersion( versionRecord.id() );
                                       access.setReadOnly( !versionRecordManager.isLatest( versionRecord ) );
                                       registeredDocumentsMenuBuilder.setReadOnly( isReadOnly() );
                                       reload();
                                   } );
    }

    void loadDocumentGraph( final ObservablePath path ) {
        view.showLoading();
        view.refreshTitle( getTitleText() );
        graphService.call( getLoadGraphContentSuccessCallback(),
                           getNoSuchFileExceptionErrorCallback() ).loadContent( path );
    }

    private RemoteCallback<GuidedDecisionTableEditorGraphContent> getLoadGraphContentSuccessCallback() {
        return ( content ) -> {
            this.content = content;
            this.originalGraphHash = content.getModel().hashCode();
            this.concurrentUpdateSessionInfo = null;
            final GuidedDecisionTableEditorGraphModel model = content.getModel();
            final Set<GuidedDecisionTableEditorGraphModel.GuidedDecisionTableGraphEntry> modelEntries = model.getEntries();

            initialiseEditorTabsWhenNoDocuments();

            if ( modelEntries == null || modelEntries.isEmpty() ) {
                view.hideBusyIndicator();
                return;
            }

            loadGraphLatch = new LoadGraphLatch( modelEntries.size(),
                                                 getSelectDecisionTableCommand( modelEntries.iterator().next().getPathHead() ),
                                                 () -> originalGraphHash = buildModelFromEditor().hashCode() );

            modelEntries.stream().forEach( loadGraphLatch::loadDocumentGraphEntry );
        };
    }

    private ParameterizedCommand<GuidedDecisionTableView.Presenter> getSelectDecisionTableCommand( final Path dtToSelectPath ) {
        return ( dtPresenter ) -> {
            if ( dtPresenter.getCurrentPath().getOriginal().equals( dtToSelectPath ) ) {
                decisionTableSelectedEvent.fire( new DecisionTableSelectedEvent( dtPresenter,
                                                                                 false ) );
            }
        };
    }

    PathPlaceRequest getPathPlaceRequest( final Path path ) {
        return new PathPlaceRequest( path );
    }

    void initialiseLockManager() {
        lockManager.init( new LockTarget( editorPath,
                                          view.asWidget(),
                                          editorPlaceRequest,
                                          () -> editorPath.getFileName() + " - " + resourceType.getDescription(),
                                          () -> {/*nothing*/} ) );
    }

    @Override
    @WorkbenchPartTitle
    public String getTitleText() {
        return versionRecordManager.getCurrentPath().getFileName() + " - " + resourceType.getDescription();
    }

    @Override
    @WorkbenchPartView
    public IsWidget getWidget() {
        return super.getWidget();
    }

    @Override
    @WorkbenchMenu
    public Menus getMenus() {
        return super.getMenus();
    }

    @Override
    @OnMayClose
    public boolean mayClose() {
        boolean mayClose = mayClose( originalGraphHash,
                                     buildModelFromEditor().hashCode() );
        mayClose = mayClose && super.mayClose();
        return mayClose;
    }

    GuidedDecisionTableEditorGraphModel buildModelFromEditor() {
        final GuidedDecisionTableEditorGraphModel model = new GuidedDecisionTableEditorGraphModel();
        for ( GuidedDecisionTableView.Presenter dtPresenter : modeller.getAvailableDecisionTables() ) {
            model.getEntries().add( new GuidedDecisionTableEditorGraphModel.GuidedDecisionTableGraphEntry( dtPresenter.getLatestPath(),
                                                                                                           dtPresenter.getCurrentPath(),
                                                                                                           dtPresenter.getView().getX(),
                                                                                                           dtPresenter.getView().getY() ) );
        }
        return model;
    }

    @Override
    @OnClose
    public void onClose() {
        lockManager.releaseLock();
        super.onClose();
    }

    @Override
    protected void onDecisionTableSelected( final @Observes DecisionTableSelectedEvent event ) {
        super.onDecisionTableSelected( event );

        if ( event.isLockRequired() ) {
            if ( !isReadOnly() ) {
                lockManager.acquireLock();
            }
        }
    }

    @Override
    public void makeMenuBar() {
        this.menus = fileMenuBuilder
                .addSave( getSaveMenuItem() )
                .addCopy( versionRecordManager::getCurrentPath,
                          fileNameValidator )
                .addRename( versionRecordManager::getPathToLatest,
                            fileNameValidator )
                .addDelete( versionRecordManager::getPathToLatest )
                .addValidate( () -> onValidate( getActiveDocument() ) )
                .addNewTopLevelMenu( getEditMenuItem() )
                .addNewTopLevelMenu( getViewMenuItem() )
                .addNewTopLevelMenu( getInsertMenuItem() )
                .addNewTopLevelMenu( getRadarMenuItem() )
                .addNewTopLevelMenu( getRegisteredDocumentsMenuItem() )
                .addNewTopLevelMenu( getVersionManagerMenuItem() )
                .build();
    }

    @Override
    protected void enableMenus( final boolean enabled ) {
        super.enableMenus( enabled );
        getRegisteredDocumentsMenuItem().setEnabled( enabled );
    }

    @Override
    public void getAvailableDocumentPaths( final Callback<List<Path>> callback ) {
        view.showLoading();
        graphService.call( new RemoteCallback<List<Path>>() {
                               @Override
                               public void callback( final List<Path> paths ) {
                                   view.hideBusyIndicator();
                                   callback.callback( paths );
                               }
                           },
                           new HasBusyIndicatorDefaultErrorCallback( view ) ).listDecisionTablesInPackage( editorPath );
    }

    @Override
    public void onOpenDocumentsInEditor( final List<Path> selectedDocumentPaths ) {
        if ( selectedDocumentPaths == null || selectedDocumentPaths.isEmpty() ) {
            return;
        }

        view.showLoading();

        loadGraphLatch = new LoadGraphLatch( selectedDocumentPaths.size(),
                                             getSelectDecisionTableCommand( selectedDocumentPaths.get( 0 ) ) );

        selectedDocumentPaths.stream().forEach( ( p ) -> {
            final PathPlaceRequest placeRequest = getPathPlaceRequest( p );
            loadGraphLatch.loadDocument( placeRequest.getPath(),
                                         placeRequest );
        } );
    }

    @Override
    protected void doSave() {
        if ( isReadOnly() ) {
            if ( versionRecordManager.isCurrentLatest() ) {
                view.alertReadOnly();
                return;
            } else {
                versionRecordManager.restoreToCurrentVersion();
                return;
            }
        }

        final Set<GuidedDecisionTableView.Presenter> allDecisionTables = new HashSet<>( modeller.getAvailableDecisionTables() );
        final Set<ObservablePath.OnConcurrentUpdateEvent> concurrentUpdateSessionInfos = new HashSet<>();
        allDecisionTables.stream().forEach( dtPresenter -> {
            final ObservablePath.OnConcurrentUpdateEvent concurrentUpdateSessionInfo = dtPresenter.getConcurrentUpdateSessionInfo();
            if ( concurrentUpdateSessionInfo != null ) {
                concurrentUpdateSessionInfos.add( concurrentUpdateSessionInfo );
            }
        } );
        if ( concurrentUpdateSessionInfo != null ) {
            concurrentUpdateSessionInfos.add( concurrentUpdateSessionInfo );
        }

        if ( !concurrentUpdateSessionInfos.isEmpty() ) {
            showConcurrentUpdatesPopup();
        } else {
            saveDocumentGraphEntries();
        }
    }

    void showConcurrentUpdatesPopup() {
        newConcurrentUpdate( concurrentUpdateSessionInfo.getPath(),
                             concurrentUpdateSessionInfo.getIdentity(),
                             this::saveDocumentGraphEntries,
                             () -> {/*Do nothing*/},
                             this::reload ).show();
    }

    void saveDocumentGraphEntries() {
        final Set<GuidedDecisionTableView.Presenter> allDecisionTables = new HashSet<>( modeller.getAvailableDecisionTables() );
        savePopUpPresenter.show( editorPath,
                                 ( commitMessage ) -> {
                                     editorView.showSaving();
                                     saveGraphLatch = new SaveGraphLatch( allDecisionTables.size(),
                                                                          commitMessage );
                                     allDecisionTables.stream().forEach( ( dtPresenter ) -> {
                                         saveGraphLatch.saveDocumentGraphEntry( dtPresenter );
                                         saveInProgressEvent.fire( new SaveInProgressEvent( dtPresenter.getLatestPath() ) );
                                     } );
                                 } );
    }

    @Override
    protected void initialiseVersionManager( final GuidedDecisionTableView.Presenter dtPresenter ) {
        //Do nothing. We maintain a single VersionRecordManager for the graph itself.
    }

    @Override
    protected void initialiseKieEditorTabs( final GuidedDecisionTableView.Presenter document,
                                            final Overview overview,
                                            final AsyncPackageDataModelOracle dmo,
                                            final Imports imports,
                                            final boolean isReadOnly ) {
        kieEditorWrapperView.clear();
        kieEditorWrapperView.addMainEditorPage( editorView );
        kieEditorWrapperView.addOverviewPage( overviewWidget,
                                              () -> overviewWidget.refresh( versionRecordManager.getVersion() ) );
        kieEditorWrapperView.addSourcePage( sourceWidget );
        kieEditorWrapperView.addImportsTab( importsWidget );
        overviewWidget.setContent( content.getOverview(),
                                   versionRecordManager.getPathToLatest() );
        importsWidget.setContent( dmo,
                                  imports,
                                  isReadOnly );
    }

    void initialiseEditorTabsWhenNoDocuments() {
        getEditMenuItem().setEnabled( false );
        getViewMenuItem().setEnabled( false );
        getInsertMenuItem().setEnabled( false );
        getRadarMenuItem().setEnabled( false );
        enableMenuItem( false,
                        MenuItems.VALIDATE );

        kieEditorWrapperView.clear();
        kieEditorWrapperView.addMainEditorPage( editorView );
        kieEditorWrapperView.addOverviewPage( overviewWidget,
                                              () -> overviewWidget.refresh( versionRecordManager.getVersion() ) );
        overviewWidget.setContent( content.getOverview(),
                                   versionRecordManager.getPathToLatest() );
    }

    void addFileChangeListeners( final ObservablePath path ) {
        path.onRename( this::onRename );
        path.onDelete( this::onDelete );

        path.onConcurrentUpdate( ( info ) -> concurrentUpdateSessionInfo = info );

        path.onConcurrentRename( ( info ) -> newConcurrentRename( info.getSource(),
                                                                  info.getTarget(),
                                                                  info.getIdentity(),
                                                                  () -> enableMenus( false ),
                                                                  this::reload ).show() );

        path.onConcurrentDelete( ( info ) -> newConcurrentDelete( info.getPath(),
                                                                  info.getIdentity(),
                                                                  () -> enableMenus( false ),
                                                                  () -> placeManager.closePlace( editorPlaceRequest ) ).show() );
    }

    void onDelete() {
        scheduleClosure( () -> placeManager.forceClosePlace( editorPlaceRequest ) );
    }

    void scheduleClosure( final Scheduler.ScheduledCommand command ) {
        Scheduler.get().scheduleDeferred( command );
    }

    void onRename() {
        reload();
        changeTitleEvent.fire( new ChangeTitleWidgetEvent( editorPlaceRequest,
                                                           getTitleText(),
                                                           editorView.getTitleWidget() ) );
    }

    void reload() {
        final List<GuidedDecisionTableView.Presenter> documents = new ArrayList<>( this.documents );
        documents.stream().forEach( this::deregisterDocument );
        modeller.getView().clear();
        modeller.releaseDecisionTables();
        loadDocumentGraph( versionRecordManager.getCurrentPath() );
    }

    void onRestore( final @Observes RestoreEvent restore ) {
        if ( versionRecordManager.getCurrentPath() == null || restore == null || restore.getPath() == null ) {
            return;
        }
        if ( versionRecordManager.getCurrentPath().equals( restore.getPath() ) ) {
            initialiseEditor( versionRecordManager.getPathToLatest(),
                              editorPlaceRequest );
            notification.fire( new NotificationEvent( CommonConstants.INSTANCE.ItemRestored() ) );
        }
    }

    private boolean isReadOnly() {
        return !this.access.isEditable();
    }

    void onUpdatedLockStatusEvent( final @Observes UpdatedLockStatusEvent event ) {
        if ( editorPath == null ) {
            return;
        }
        if ( editorPath.equals( event.getFile() ) ) {
            if ( event.isLocked() ) {
                access.setLock( event.isLockedByCurrentUser() ? CURRENT_USER : OTHER_USER );
            } else {
                access.setLock( NOBODY );
            }
        }
    }

}