/*
 * Copyright 2012 Red Hat, Inc. and/or its affiliates.
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

import com.google.gwt.event.shared.GwtEvent;

public class ExpressionChangeEvent extends GwtEvent<ExpressionChangeHandler> {

    private static final GwtEvent.Type<ExpressionChangeHandler> TYPE = new GwtEvent.Type<ExpressionChangeHandler>();

    @Override
    protected void dispatch( ExpressionChangeHandler handler ) {
        handler.onExpressionChanged( this );
    }

    @Override
    public GwtEvent.Type<ExpressionChangeHandler> getAssociatedType() {
        return getType();
    }

    public static final Type<ExpressionChangeHandler> getType() {
        return TYPE;
    }
}
