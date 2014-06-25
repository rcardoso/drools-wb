/*
 * Copyright 2012 JBoss Inc
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

package org.drools.workbench.screens.guided.rule.client.editor;

import java.util.ArrayList;
import java.util.List;

import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import org.drools.workbench.models.datamodel.oracle.DataType;
import org.drools.workbench.models.datamodel.oracle.DropDownData;
import org.drools.workbench.models.datamodel.rule.ActionFieldFunction;
import org.drools.workbench.models.datamodel.rule.FieldNatureType;
import org.drools.workbench.screens.guided.rule.client.editor.util.SuperTypeMatcher;
import org.drools.workbench.screens.guided.rule.client.resources.GuidedRuleEditorResources;
import org.drools.workbench.screens.guided.rule.client.resources.images.GuidedRuleEditorImages508;
import org.drools.workbench.screens.guided.rule.client.widget.EnumDropDown;
import org.kie.workbench.common.widgets.client.datamodel.AsyncPackageDataModelOracle;
import org.kie.workbench.common.widgets.client.widget.TextBoxFactory;
import org.uberfire.client.callbacks.Callback;
import org.kie.uberfire.client.common.DirtyableComposite;
import org.kie.uberfire.client.common.DropDownValueChanged;
import org.kie.uberfire.client.common.FormStylePopup;
import org.kie.uberfire.client.common.InfoPopup;
import org.kie.uberfire.client.common.SmallLabel;

/**
 * This provides for editing of fields in the RHS of a rule.
 */
public class MethodParameterValueEditor
        extends DirtyableComposite {

    private AsyncPackageDataModelOracle oracle;
    private ActionFieldFunction methodParameter;
    private DropDownData enums;
    private SimplePanel root = new SimplePanel();
    private RuleModeller model = null;
    private String parameterType = null;
    private Command onValueChangeCommand = null;

    public MethodParameterValueEditor(
            final AsyncPackageDataModelOracle oracle,
            final ActionFieldFunction val,
            final DropDownData enums,
            RuleModeller model,
            Command onValueChangeCommand) {
        this.oracle = oracle;
        this.methodParameter = val;
        this.model = model;
        this.parameterType = val.getType();
        this.onValueChangeCommand = onValueChangeCommand;
        setEnums(enums);
        refresh();
        initWidget(root);
    }

    private void setEnums(DropDownData enums) {
        if (methodParameter.getType().equals(DataType.TYPE_BOOLEAN)) {
            this.enums = DropDownData.create(new String[]{"true", "false"});
        } else {
            this.enums = enums;
        }
    }

    private void refresh() {
        root.clear();
        if (enums != null && (enums.getFixedList() != null || enums.getQueryExpression() != null)) {
            root.add(new EnumDropDown(methodParameter.getValue(),
                    new DropDownValueChanged() {
                        public void valueChanged(String newText,
                                String newValue) {
                            setMethodParameterValue(newValue);
                        }
                    },
                    enums));
        } else {

            if (methodParameter.getNature() == FieldNatureType.TYPE_UNDEFINED && methodParameter.getValue() == null) {
                // we have a blank slate..
                // have to give them a choice
                root.add(choice());
            } else {
                if (methodParameter.getNature() == FieldNatureType.TYPE_VARIABLE) {
                    root.add(boundVariable());
                } else if (methodParameter.getNature() == FieldNatureType.TYPE_FORMULA) {
                    root.add(boundFormulaTextBox());
                } else {
                    root.add(boundLiteralTextBox());
                }

            }

        }
    }

    private ListBox boundVariable() {
        BoundListBox boundListBox = new BoundListBox(model, methodParameter, new SuperTypeMatcher(oracle));

        boundListBox.addChangeHandler(new ChangeHandler() {

            public void onChange(ChangeEvent event) {
                ListBox w = (ListBox) event.getSource();

                setMethodParameterValue(w.getValue(w.getSelectedIndex()));
                refresh();
            }

        });
        return boundListBox;
    }

    private TextBox boundLiteralTextBox() {
        final TextBox box = TextBoxFactory.getTextBox(methodParameter.getType());

        // We need both handlers, since The textbox TextBoxFactory can return a box that changes the value in itself
        box.addValueChangeHandler(new ValueChangeHandler<String>() {
            @Override
            public void onValueChange(ValueChangeEvent<String> event) {
                setMethodParameterValue(box.getValue());
            }
        });
        box.addKeyUpHandler(new KeyUpHandler() {
            @Override
            public void onKeyUp(KeyUpEvent event) {
                setMethodParameterValue(box.getValue());
            }
        });

        box.setStyleName("constraint-value-Editor");
        if (this.methodParameter.getValue() != null || this.methodParameter.getValue().isEmpty()) {
            box.setValue(this.methodParameter.getValue());
        }

        // This updates the model
        setMethodParameterValue(box.getValue());

        return box;
    }

    private TextBox boundFormulaTextBox() {
        final TextBox box = new TextBox();
        box.setStyleName("constraint-value-Editor");
        if (this.methodParameter.getValue() == null) {
            box.setValue("");
        } else {
            box.setValue(this.methodParameter.getValue());
        }

        box.addKeyUpHandler(new KeyUpHandler() {
            @Override
            public void onKeyUp(KeyUpEvent event) {
                setMethodParameterValue(box.getValue());
            }
        });

        return box;
    }

    private void setMethodParameterValue(String value) {
        methodParameter.setValue(value);
        if (onValueChangeCommand != null) {
            onValueChangeCommand.execute();
        }
        makeDirty();
    }

    private Widget choice() {
        Image clickme = GuidedRuleEditorImages508.INSTANCE.Edit();
        clickme.addClickHandler(new ClickHandler() {

            public void onClick(ClickEvent event) {
                showTypeChoice();
            }
        });
        return clickme;
    }

    protected void showTypeChoice() {
        final FormStylePopup form = new FormStylePopup(GuidedRuleEditorImages508.INSTANCE.Wizard(),
                GuidedRuleEditorResources.CONSTANTS.FieldValue());

        //Literal values
        Button lit = new Button(GuidedRuleEditorResources.CONSTANTS.LiteralValue());
        lit.addClickHandler(new ClickHandler() {

            public void onClick(ClickEvent event) {
                methodParameter.setNature(FieldNatureType.TYPE_LITERAL);
                methodParameter.setValue("");
                makeDirty();
                refresh();
                form.hide();
            }

        });

        form.addAttribute(GuidedRuleEditorResources.CONSTANTS.LiteralValue() + ":",
                widgets(lit,
                        new InfoPopup(GuidedRuleEditorResources.CONSTANTS.Literal(),
                                GuidedRuleEditorResources.CONSTANTS.LiteralValTip())));

        canTheVariableButtonBeShown(new Callback<Boolean>() {
            @Override public void callback(Boolean result) {

                if (result) {
                    addBoundVariableButton(form);

                    form.addRow(new HTML("<hr/>"));
                    form.addRow(new SmallLabel(GuidedRuleEditorResources.CONSTANTS.AdvancedSection()));
                }

                //Formulas
                Button formula = new Button(GuidedRuleEditorResources.CONSTANTS.NewFormula());
                formula.addClickHandler(new ClickHandler() {

                    public void onClick(ClickEvent event) {
                        methodParameter.setNature(FieldNatureType.TYPE_FORMULA);
                        makeDirty();
                        refresh();
                        form.hide();
                    }
                });

                form.addAttribute(GuidedRuleEditorResources.CONSTANTS.AFormula() + ":",
                        widgets(formula,
                                new InfoPopup(GuidedRuleEditorResources.CONSTANTS.AFormula(),
                                        GuidedRuleEditorResources.CONSTANTS.FormulaExpressionTip())));

                form.show();
            }
        });

    }

    private void addBoundVariableButton(
            final FormStylePopup form) {
        form.addRow(new HTML("<hr/>"));
        form.addRow(new SmallLabel(GuidedRuleEditorResources.CONSTANTS.AdvancedSection()));
        Button variableButton = new Button(GuidedRuleEditorResources.CONSTANTS.BoundVariable());
        form.addAttribute(GuidedRuleEditorResources.CONSTANTS.BoundVariable() + ":",
                variableButton);
        variableButton.addClickHandler(new ClickHandler() {

            public void onClick(ClickEvent event) {
                methodParameter.setNature(FieldNatureType.TYPE_VARIABLE);
                methodParameter.setValue("=");
                makeDirty();
                refresh();
                form.hide();
            }

        });
    }

    private void canTheVariableButtonBeShown(
            final Callback<Boolean> callback) {

        List<String> factTypes = new ArrayList<String>();

        for (String variable : model.getModel().getAllVariables()) {
            String factType = getFactType(variable);

            factTypes.add(factType);

            if (factType.equals(this.parameterType)) {
                callback.callback(true);
                return;
            }
        }

        new SuperTypeMatcher(oracle).isThereAMatchingSuperType(factTypes, parameterType, callback);
    }

    private String getFactType(String variable) {
        if (model.getModel().getRHSBoundFacts().contains(variable) == false) {
            return model.getModel().getLHSBindingType(variable);

        } else {
            return model.getModel().getRHSBoundFact(variable).getFactType();
        }
    }

    private Widget widgets(Button lit,
            InfoPopup popup) {
        HorizontalPanel h = new HorizontalPanel();
        h.add(lit);
        h.add(popup);
        return h;
    }

}
