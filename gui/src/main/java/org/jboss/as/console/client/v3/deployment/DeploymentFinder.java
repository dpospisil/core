/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.console.client.v3.deployment;

import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.ContentSlot;
import com.gwtplatform.mvp.client.annotations.NameToken;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.proxy.PlaceManager;
import com.gwtplatform.mvp.client.proxy.ProxyPlace;
import com.gwtplatform.mvp.client.proxy.RevealContentEvent;
import com.gwtplatform.mvp.client.proxy.RevealContentHandler;
import com.gwtplatform.mvp.shared.proxy.PlaceRequest;
import org.jboss.as.console.client.core.Header;
import org.jboss.as.console.client.core.MainLayoutPresenter;
import org.jboss.as.console.client.core.NameTokens;
import org.jboss.as.console.client.domain.model.ServerGroupRecord;
import org.jboss.as.console.client.rbac.UnauthorisedPresenter;
import org.jboss.as.console.client.shared.state.PerspectivePresenter;
import org.jboss.as.console.client.v3.presenter.Finder;
import org.jboss.as.console.client.v3.stores.domain.ServerGroupStore;
import org.jboss.as.console.client.v3.stores.domain.actions.RefreshServerGroups;
import org.jboss.as.console.client.widgets.nav.v3.ClearFinderSelectionEvent;
import org.jboss.as.console.client.widgets.nav.v3.FinderScrollEvent;
import org.jboss.as.console.client.widgets.nav.v3.PreviewEvent;
import org.jboss.as.console.spi.OperationMode;
import org.jboss.as.console.spi.RequiredResources;
import org.jboss.as.console.spi.SearchIndex;
import org.jboss.gwt.circuit.Dispatcher;

import static org.jboss.as.console.spi.OperationMode.Mode.DOMAIN;

/**
 * @author Harald Pehl
 */
public class DeploymentFinder
        extends PerspectivePresenter<DeploymentFinder.MyView, DeploymentFinder.MyProxy>
        implements Finder, PreviewEvent.Handler, FinderScrollEvent.Handler, ClearFinderSelectionEvent.Handler {


    // @formatter:off --------------------------------------- proxy & view

    @ProxyCodeSplit
    @OperationMode(DOMAIN)
    @NameToken(NameTokens.DeploymentFinder)
    @SearchIndex(keywords = {"deployment", "war", "ear", "application"})
    @RequiredResources(resources = {
            "/deployment=*",
            //"/{selected.host}/server=*", TODO: https://issues.jboss.org/browse/WFLY-1997
            "/server-group={selected.group}/deployment=*"
    }, recursive = false)
    public interface MyProxy extends ProxyPlace<DeploymentFinder> {
    }

    public interface MyView extends View {
        void updateServerGroups(Iterable<ServerGroupRecord> serverGroups);
        void updateAssignments(Iterable<Assignment> assignments);
        void updateDeployments(Iterable<Deployment> deployments);
        void updateSubdeployments(Iterable<Deployment> subdeployments);

        void setPreview(SafeHtml html);
        void clearActiveSelection(ClearFinderSelectionEvent event);
        void toggleScrolling(boolean enforceScrolling, int requiredWidth);
    }

    // @formatter:on ---------------------------------------- instance data


    @ContentSlot
    public static final GwtEvent.Type<RevealContentHandler<?>> TYPE_MainContent = new GwtEvent.Type<>();

    private final Dispatcher circuit;
    private final DeploymentStore deploymentStore;
    private final ServerGroupStore serverGroupStore;


    // ------------------------------------------------------ presenter lifecycle

    @Inject
    public DeploymentFinder(final EventBus eventBus, final MyView view, final MyProxy proxy,
            final Dispatcher circuit, final PlaceManager placeManager, final Header header,
            final UnauthorisedPresenter unauthorisedPresenter,
            final DeploymentStore deploymentStore, final ServerGroupStore serverGroupStore) {
        super(eventBus, view, proxy, placeManager, header, NameTokens.DeploymentFinder,
                unauthorisedPresenter, TYPE_MainContent);
        this.circuit = circuit;
        this.deploymentStore = deploymentStore;
        this.serverGroupStore = serverGroupStore;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void onBind() {
        super.onBind();

        // GWT event handler
        registerHandler(getEventBus().addHandler(PreviewEvent.TYPE, this));
        registerHandler(getEventBus().addHandler(FinderScrollEvent.TYPE, this));
        registerHandler(getEventBus().addHandler(ClearFinderSelectionEvent.TYPE, this));

        // circuit handler
        registerHandler(deploymentStore.addChangeHandler(action -> {
            if (action instanceof LoadAssignments) {
                getView().updateAssignments(deploymentStore.getAssignments());
            } else if (action instanceof LoadDeployments) {
                getView().updateDeployments(deploymentStore.getDeployments());
            }
        }));
        registerHandler(serverGroupStore.addChangeHandler(RefreshServerGroups.class, action -> {
            getView().updateServerGroups(serverGroupStore.getServerGroups());
        }));
    }

    @Override
    protected void onFirstReveal(final PlaceRequest placeRequest, final PlaceManager placeManager,
            final boolean revealDefault) {
        circuit.dispatch(new ResetDeploymentStore());
        circuit.dispatch(new RefreshServerGroups());
    }

    @Override
    protected void revealInParent() {
        RevealContentEvent.fire(this, MainLayoutPresenter.TYPE_MainContent, this);
    }


    // ------------------------------------------------------ finder related methods

    @Override
    public void onPreview(PreviewEvent event) {
        getView().setPreview(event.getHtml());
    }

    @Override
    public void onToggleScrolling(final FinderScrollEvent event) {
        getView().toggleScrolling(event.isEnforceScrolling(), event.getRequiredWidth());
    }


    @Override
    public void onClearActiveSelection(final ClearFinderSelectionEvent event) {
        getView().clearActiveSelection(event);
    }
}
