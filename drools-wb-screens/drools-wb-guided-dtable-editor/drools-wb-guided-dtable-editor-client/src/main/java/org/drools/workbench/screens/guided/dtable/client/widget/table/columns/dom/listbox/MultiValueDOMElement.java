/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates.
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
package org.drools.workbench.screens.guided.dtable.client.widget.table.columns.dom.listbox;

import com.google.gwt.dom.client.Style;
import com.google.gwt.user.client.ui.ListBox;
import org.drools.workbench.screens.guided.dtable.client.widget.table.model.GuidedDecisionTableUiCell;
import org.uberfire.ext.wires.core.grids.client.widget.context.GridBodyCellRenderContext;
import org.uberfire.ext.wires.core.grids.client.widget.dom.impl.BaseDOMElement;
import org.uberfire.ext.wires.core.grids.client.widget.grid.GridWidget;
import org.uberfire.ext.wires.core.grids.client.widget.layer.GridLayer;

/**
 * A DOMElement that can contain multiple values; e.g. a multi-select ListBox.
 */
public abstract class MultiValueDOMElement<T, W extends ListBox> extends BaseDOMElement<T, W> {

    private static final int HEIGHT = 16;

    public MultiValueDOMElement( final W widget,
                                 final GridLayer gridLayer,
                                 final GridWidget gridWidget ) {
        super( widget,
               gridLayer,
               gridWidget );
        final Style style = widget.getElement().getStyle();
        style.setWidth( 100,
                        Style.Unit.PCT );
        style.setHeight( HEIGHT,
                         Style.Unit.PX );
        style.setPaddingLeft( 2,
                              Style.Unit.PX );
        style.setPaddingRight( 2,
                               Style.Unit.PX );
        style.setFontSize( 10,
                           Style.Unit.PX );

        // --- Workaround for BS2 ---
        style.setPosition( Style.Position.RELATIVE );
        style.setPaddingTop( 0,
                             Style.Unit.PX );
        style.setPaddingBottom( 0,
                                Style.Unit.PX );
        style.setProperty( "WebkitBoxSizing",
                           "border-box" );
        style.setProperty( "MozBoxSizing",
                           "border-box" );
        style.setProperty( "boxSizing",
                           "border-box" );
        style.setProperty( "lineHeight",
                           "normal" );
        // --- End workaround ---

        getContainer().getElement().getStyle().setPaddingLeft( 5,
                                                               Style.Unit.PX );
        getContainer().getElement().getStyle().setPaddingRight( 5,
                                                                Style.Unit.PX );
        getContainer().setWidget( widget );
    }

    @Override
    public void initialise( final GridBodyCellRenderContext context ) {
        final Style style = widget.getElement().getStyle();
        style.setMarginTop( ( context.getCellHeight() - HEIGHT ) / 2,
                            Style.Unit.PX );
        transform( context );
    }

    @Override
    public void flush( final T value ) {
        final int rowIndex = context.getRowIndex();
        final int columnIndex = context.getColumnIndex();
        if ( value == null ) {
            gridWidget.getModel().deleteCell( rowIndex,
                                              columnIndex );

        } else {
            gridWidget.getModel().setCell( rowIndex,
                                           columnIndex,
                                           new GuidedDecisionTableUiCell<T>( value ) );
        }
    }

}
