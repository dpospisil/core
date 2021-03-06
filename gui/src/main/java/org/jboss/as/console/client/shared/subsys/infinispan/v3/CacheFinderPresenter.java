package org.jboss.as.console.client.shared.subsys.infinispan.v3;

/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */

import com.allen_sauer.gwt.log.client.Log;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.Presenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.NameToken;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.proxy.Place;
import com.gwtplatform.mvp.client.proxy.Proxy;
import com.gwtplatform.mvp.client.proxy.RevealContentEvent;
import org.jboss.as.console.client.Console;
import org.jboss.as.console.client.core.NameTokens;
import org.jboss.as.console.client.domain.model.SimpleCallback;
import org.jboss.as.console.client.domain.profiles.ProfileMgmtPresenter;
import org.jboss.as.console.client.rbac.SecurityFramework;
import org.jboss.as.console.client.shared.subsys.Baseadress;
import org.jboss.as.console.client.shared.subsys.RevealStrategy;
import org.jboss.as.console.client.standalone.ServerMgmtApplicationPresenter;
import org.jboss.as.console.client.v3.ResourceDescriptionRegistry;
import org.jboss.as.console.client.v3.dmr.AddressTemplate;
import org.jboss.as.console.client.v3.dmr.ResourceAddress;
import org.jboss.as.console.client.v3.dmr.ResourceDescription;
import org.jboss.as.console.client.v3.widgets.AddResourceDialog;
import org.jboss.as.console.mbui.behaviour.CoreGUIContext;
import org.jboss.as.console.mbui.behaviour.ModelNodeAdapter;
import org.jboss.as.console.spi.AccessControl;
import org.jboss.as.console.spi.SearchIndex;
import org.jboss.ballroom.client.rbac.SecurityContext;
import org.jboss.ballroom.client.widgets.window.DefaultWindow;
import org.jboss.dmr.client.ModelNode;
import org.jboss.dmr.client.Property;
import org.jboss.dmr.client.dispatch.DispatchAsync;
import org.jboss.dmr.client.dispatch.impl.DMRAction;
import org.jboss.dmr.client.dispatch.impl.DMRResponse;
import org.useware.kernel.gui.behaviour.StatementContext;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.jboss.dmr.client.ModelDescriptionConstants.*;
import static org.jboss.dmr.client.ModelDescriptionConstants.RESULT;


/**
 * The Presenter for Cache Containers
 *
 * @author Heiko Braun
 */
public class CacheFinderPresenter extends Presenter<CacheFinderPresenter.MyView, CacheFinderPresenter.MyProxy> {

    private RevealStrategy revealStrategy;
    private final DispatchAsync dispatcher;
    private final ResourceDescriptionRegistry descriptionRegistry;
    private final SecurityFramework securityFramework;
    private final StatementContext statementContext;

    private static AddressTemplate CACHE_CONTAINER = AddressTemplate.of("{selected.profile}/subsystem=infinispan/cache-container=*");
    private DefaultWindow transportDialog;
    private TransportView transportView;
    private DefaultWindow containerDialog;
    private ContainerView containerView;


    @ProxyCodeSplit
    @NameToken(NameTokens.CacheFinderPresenter)
    @AccessControl(resources = {
            "{selected.profile}/subsystem=infinispan",
            "{selected.profile}/subsystem=infinispan/cache-container=*",
            "{selected.profile}/subsystem=infinispan/cache-container=*/transport=TRANSPORT"
    }, recursive = false)
    @SearchIndex(keywords = {
            "cache", "ejb", "hibernate", "web", "transport"
    })
    public interface MyProxy extends Proxy<CacheFinderPresenter>, Place {
    }

    public interface MyView extends View {
        void setPresenter(CacheFinderPresenter presenter);
        public void updateFrom(List<Property> list);
        public void setPreview(final SafeHtml html);
    }

    @Inject
    public CacheFinderPresenter(
            EventBus eventBus, MyView view, MyProxy proxy,
            RevealStrategy revealStrategy, DispatchAsync dispatcher,
            ResourceDescriptionRegistry descriptionRegistry, SecurityFramework securityFramework, CoreGUIContext delegate) {
        super(eventBus, view, proxy);

        this.revealStrategy = revealStrategy;

        this.dispatcher = dispatcher;
        this.descriptionRegistry = descriptionRegistry;
        this.securityFramework = securityFramework;
        this.statementContext = delegate;
    }

    @Override
    protected void onBind() {
        super.onBind();
        getView().setPresenter(this);
    }

    @Override
    protected void onReset() {
        super.onReset();

        loadContainer(null);

    }

    public ResourceDescriptionRegistry getDescriptionRegistry() {
        return descriptionRegistry;
    }

    public SecurityFramework getSecurityFramework() {
        return securityFramework;
    }

    private void loadContainer() {
        loadContainer(null);
    }

    private void loadContainer(String pref) {
        ModelNode operation = new ModelNode();
        operation.get(OP).set(READ_CHILDREN_RESOURCES_OPERATION);
        operation.get(ADDRESS).set(Baseadress.get());
        operation.get(ADDRESS).add("subsystem", "infinispan");
        operation.get(CHILD_TYPE).set("cache-container");
        operation.get(RECURSIVE).set(true);
        operation.get("recursive-depth").set("2");

        dispatcher.execute(new DMRAction(operation), new SimpleCallback<DMRResponse>() {

            @Override
            public void onSuccess(DMRResponse result) {
                ModelNode response = result.get();

                if (response.isFailure()) {
                    Log.error("Failed to load cache container", response.getFailureDescription());
                    getView().updateFrom(Collections.EMPTY_LIST);
                } else {
                    List<Property> containers = response.get(RESULT).asPropertyList();
                    getView().updateFrom(containers);


                    // the transport view can still be visible
                    if(transportView!=null && pref!=null)
                    {

                        for (Property container : containers) {
                            if(container.getName().equals(pref))
                            {
                                transportView.updateFrom(container.getValue().get("transport").get("TRANSPORT"));
                                break;
                            }
                        }

                    }
                }



            }
        });
    }

    @Override
    protected void revealInParent() {
        if(Console.getBootstrapContext().isStandalone())
            RevealContentEvent.fire(this, ServerMgmtApplicationPresenter.TYPE_MainContent, this);
        else
            RevealContentEvent.fire(this, ProfileMgmtPresenter.TYPE_MainContent, this);
    }

    public void onCreateTransport(AddressTemplate address, Property cacheContainer, ModelNode entity) {

        ResourceAddress fqAddress = address.resolve(statementContext, cacheContainer.getName());

        entity.get(OP).set(ADD);
        entity.get(ADDRESS).set(fqAddress);

        dispatcher.execute(new DMRAction(entity), new SimpleCallback<DMRResponse>() {

            @Override
            public void onSuccess(DMRResponse result) {
                ModelNode response = result.get();

                if (response.isFailure())
                {
                    Console.error("Failed to create resource "+fqAddress, response.getFailureDescription());
                }
                else
                {

                    Console.info("Successfully created "+fqAddress);
                }

                loadContainer();
            }
        });
    }

    public void onRemove(Property cacheContainer) {
        ResourceAddress fqAddress = CACHE_CONTAINER.resolve(statementContext, cacheContainer.getName());

        ModelNode op = new ModelNode();
        op.get(OP).set(REMOVE);
        op.get(ADDRESS).set(fqAddress);

        dispatcher.execute(new DMRAction(op), new SimpleCallback<DMRResponse>() {

            @Override
            public void onFailure(Throwable caught) {
                super.onFailure(caught);
                loadContainer();
            }

            @Override
            public void onSuccess(DMRResponse dmrResponse) {

                ModelNode response = dmrResponse.get();
                if(response.isFailure())
                {
                    Console.error("Failed to remove resource "+fqAddress, response.getFailureDescription());
                }
                else
                {
                    Console.info("Successfully removed " + fqAddress);
                }

                loadContainer();
            }
        });

    }

    public void onLauchAddContainer() {
        final SecurityContext securityContext =
                securityFramework.getSecurityContext(getProxy().getNameToken());

        final ResourceDescription resourceDescription = descriptionRegistry.lookup(CACHE_CONTAINER);

        final DefaultWindow dialog = new DefaultWindow("New Cache Configuration");
        AddResourceDialog addDialog = new AddResourceDialog(securityContext, resourceDescription,
                new AddResourceDialog.Callback() {
                    @Override
                    public void onAdd(ModelNode payload) {
                        dialog.hide();

                        final ResourceAddress fqAddress =
                                CACHE_CONTAINER.resolve(statementContext, payload.get("name").asString());

                        payload.get(OP).set(ADD);
                        payload.get(ADDRESS).set(fqAddress);

                        dispatcher.execute(new DMRAction(payload), new SimpleCallback<DMRResponse>() {

                            @Override
                            public void onFailure(Throwable caught) {
                                super.onFailure(caught);
                                loadContainer();
                            }

                            @Override
                            public void onSuccess(DMRResponse dmrResponse) {
                                Console.info("Successfully added "+fqAddress);
                                loadContainer();
                            }
                        });


                    }

                    @Override
                    public void onCancel() {
                        dialog.hide();
                    }
                });

        dialog.setWidth(640);
        dialog.setHeight(480);
        dialog.setWidget(addDialog);
        dialog.setGlassEnabled(true);
        dialog.center();
    }

    public void onLaunchTransportSettings(Property cacheContainer) {
        transportDialog = new DefaultWindow("Transport Settings");

        transportView = new TransportView(this, cacheContainer);

        transportDialog.setWidth(640);
        transportDialog.setHeight(480);
        transportDialog.trapWidget(transportView.asWidget());
        transportDialog.setGlassEnabled(true);
        transportDialog.center();

        if(cacheContainer.getValue().hasDefined("transport"))
            transportView.updateFrom(cacheContainer.getValue().get("transport").get("TRANSPORT"));
        else
            transportView.updateFrom(new ModelNode());
    }

    public void onSaveTransport(AddressTemplate address, Property cacheContainer, Map<String, Object> changeset) {
        ResourceAddress fqAddress = address.resolve(statementContext, cacheContainer.getName());

        final ModelNodeAdapter adapter = new ModelNodeAdapter();
        ModelNode operation = adapter.fromChangeset(changeset, fqAddress);

        dispatcher.execute(new DMRAction(operation), new AsyncCallback<DMRResponse>() {
            @Override
            public void onFailure(Throwable caught) {
                Console.error("Failed to modify resource "+fqAddress, caught.getMessage());
            }

            @Override
            public void onSuccess(DMRResponse dmrResponse) {
                ModelNode response = dmrResponse.get();
                if (response.isFailure()) {
                    Console.error("Failed to modify resource " + fqAddress, response.getFailureDescription());
                }
                else {
                    Console.info("Successfully modified "+fqAddress);
                }

                loadContainer();
            }
        });
    }

    public void onLaunchContainerSettings(Property cacheContainer) {
        containerDialog = new DefaultWindow("Container Settings");

        containerView = new ContainerView(this, cacheContainer);

        containerDialog.setWidth(640);
        containerDialog.setHeight(480);
        containerDialog.trapWidget(containerView.asWidget());
        containerDialog.setGlassEnabled(true);
        containerDialog.center();


        containerView.updateFrom(cacheContainer.getValue());

    }
}

