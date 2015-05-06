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
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.PopupPanel;
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
import org.jboss.as.console.client.Console;
import org.jboss.as.console.client.core.HasPresenter;
import org.jboss.as.console.client.core.Header;
import org.jboss.as.console.client.core.MainLayoutPresenter;
import org.jboss.as.console.client.core.NameTokens;
import org.jboss.as.console.client.domain.groups.deployment.ServerGroupSelection;
import org.jboss.as.console.client.domain.groups.deployment.ServerGroupSelector;
import org.jboss.as.console.client.domain.model.ServerGroupRecord;
import org.jboss.as.console.client.domain.model.ServerInstance;
import org.jboss.as.console.client.domain.model.SimpleCallback;
import org.jboss.as.console.client.domain.topology.HostInfo;
import org.jboss.as.console.client.domain.topology.TopologyFunctions;
import org.jboss.as.console.client.rbac.UnauthorisedPresenter;
import org.jboss.as.console.client.shared.BeanFactory;
import org.jboss.as.console.client.shared.deployment.DeployCommandExecutor;
import org.jboss.as.console.client.shared.deployment.DeploymentCommand;
import org.jboss.as.console.client.shared.deployment.DeploymentStore;
import org.jboss.as.console.client.shared.deployment.NewDeploymentWizard;
import org.jboss.as.console.client.shared.deployment.model.ContentRepository;
import org.jboss.as.console.client.shared.deployment.model.DeploymentRecord;
import org.jboss.as.console.client.shared.flow.FunctionContext;
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
import org.jboss.ballroom.client.widgets.window.DefaultWindow;
import org.jboss.ballroom.client.widgets.window.Feedback;
import org.jboss.dmr.client.ModelNode;
import org.jboss.dmr.client.dispatch.DispatchAsync;
import org.jboss.dmr.client.dispatch.impl.DMRAction;
import org.jboss.dmr.client.dispatch.impl.DMRResponse;
import org.jboss.gwt.circuit.Action;
import org.jboss.gwt.circuit.Dispatcher;
import org.jboss.gwt.circuit.PropagatesChange;
import org.jboss.gwt.flow.client.Async;
import org.jboss.gwt.flow.client.Outcome;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static org.jboss.as.console.spi.OperationMode.Mode.DOMAIN;
import static org.jboss.dmr.client.ModelDescriptionConstants.*;

/**
 * TODO Remove dependency to PerspectivePresenter
 *
 * @author Harald Pehl
 */
public class DeploymentFinder
        extends PerspectivePresenter<DeploymentFinder.MyView, DeploymentFinder.MyProxy>
        implements DeployCommandExecutor, Finder, PreviewEvent.Handler, FinderScrollEvent.Handler, ClearFinderSelectionEvent.Handler {


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

    public interface MyView extends View, HasPresenter<DeploymentFinder> {
        void setPreview(SafeHtml html);
        void toggleScrolling(boolean enforceScrolling, int requiredWidth);
        void updateServerGroups(List<ServerGroupRecord> serverGroups);
        void updateAssignments(List<DeploymentRecord> deployments);
        void updateSubdeployments(ServerInstance referenceServer, List<DeploymentRecord> subdeployments);
        void clearActiveSelection(ClearFinderSelectionEvent event);
        void updateDeployment(ServerInstance referenceServer, DeploymentRecord deployment);

        void setReferenceServer(ServerInstance server);

        void noServerFound();
    }

    // @formatter:on ---------------------------------------- instance data


    @ContentSlot
    public static final GwtEvent.Type<RevealContentHandler<?>> TYPE_MainContent = new GwtEvent.Type<RevealContentHandler<?>>();
    private final DispatchAsync dispatcher;
    private final PlaceManager placeManager;
    private final BeanFactory beanFactory;
    private final DeploymentStore deploymentStore;
    private final ServerGroupStore serverGroupStore;
    private ContentRepository contentRepository;
    private DefaultWindow window;
    private final Dispatcher circuit;


    // ------------------------------------------------------ presenter lifecycle

    @Inject
    public DeploymentFinder(final EventBus eventBus, final MyView view, final MyProxy proxy,
                            final DispatchAsync dispatcher, final PlaceManager placeManager,
                            final Dispatcher circuit,
                            final BeanFactory beanFactory, final Header header, final UnauthorisedPresenter unauthorisedPresenter,
                            final DeploymentStore deploymentStore, ServerGroupStore serverGroupStore) {
        super(eventBus, view, proxy, placeManager, header, NameTokens.DeploymentFinder,
                unauthorisedPresenter, TYPE_MainContent);
        this.dispatcher = dispatcher;
        this.placeManager = placeManager;
        this.circuit = circuit;
        this.beanFactory = beanFactory;
        this.deploymentStore = deploymentStore;
        this.serverGroupStore = serverGroupStore;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void onBind() {
        super.onBind();
        getView().setPresenter(this);
        registerHandler(getEventBus().addHandler(PreviewEvent.TYPE, this));
        registerHandler(getEventBus().addHandler(FinderScrollEvent.TYPE, this));
        registerHandler(getEventBus().addHandler(ClearFinderSelectionEvent.TYPE, this));

        serverGroupStore.addChangeHandler(new PropagatesChange.Handler() {
            @Override
            public void onChange(final Action action) {
                getView().updateServerGroups(serverGroupStore.getServerGroups());
            }
        });
    }

    @Override
    protected void onFirstReveal(final PlaceRequest placeRequest, final PlaceManager placeManager,
                                 final boolean revealDefault) {
        circuit.dispatch(new RefreshServerGroups());
    }

    @Override
    protected void revealInParent() {
        RevealContentEvent.fire(this, MainLayoutPresenter.TYPE_MainContent, this);
    }

    @Override
    protected void onReset() {
        super.onReset();
    }

    public PlaceManager getPlaceManager() {
        return placeManager;
    }


    // ------------------------------------------------------ deployment related methods

    public void loadAssignmentsFor(final ServerGroupRecord serverGroup) {

        // reset
        getView().setReferenceServer(null);

        loadReference(serverGroup.getName(), new AsyncCallback<ServerInstance>() {
            @Override
            public void onFailure(Throwable throwable) {
                Console.error("No reference server found!");

                deploymentStore.loadContentRepository(new SimpleCallback<ContentRepository>() {
                    @Override
                    public void onSuccess(final ContentRepository result) {
                        contentRepository = result;
                        List<DeploymentRecord> deployments = result.getDeployments(serverGroup);
                        getView().updateAssignments(deployments);
                    }
                });
            }

            @Override
            public void onSuccess(final ServerInstance serverInstance) {

                deploymentStore.loadContentRepository(new SimpleCallback<ContentRepository>() {
                    @Override
                    public void onSuccess(final ContentRepository result) {
                        contentRepository = result;
                        getView().setReferenceServer(serverInstance);
                        List<DeploymentRecord> deployments = result.getDeployments(serverGroup);
                        getView().updateAssignments(deployments);
                    }
                });
            }
        });

    }

    @Override
    public void onAssignToServerGroup(final DeploymentRecord deployment, final boolean enable,
                                      final Set<ServerGroupSelection> selectedGroups) {

        final PopupPanel loading = Feedback.loading(
                Console.CONSTANTS.common_label_plaseWait(),
                Console.CONSTANTS.common_label_requestProcessed(),
                () -> {});

        Set<String> names = new HashSet<>();
        for (ServerGroupSelection group : selectedGroups) { names.add(group.getName()); }

        deploymentStore.addToServerGroups(names, enable, deployment, new SimpleCallback<DMRResponse>() {
            @Override
            public void onFailure(final Throwable caught) {
                loading.hide();
                Console.error(Console.MESSAGES.addingFailed("Deployment " + deployment.getRuntimeName()),
                        caught.getMessage());
            }

            @Override
            public void onSuccess(DMRResponse response) {
                loading.hide();
                ModelNode result = response.get();
                if (result.isFailure()) {
                    Console.error(Console.MESSAGES.addingFailed("Deployment " + deployment.getRuntimeName()),
                            result.getFailureDescription());
                } else {
                    Console.info(Console.MESSAGES
                            .added("Deployment " + deployment.getRuntimeName() + " to group " + selectedGroups));
                }
                //                loadAssignmentsFor(deployment);
            }
        });
    }

    @Override
    public void enableDisableDeployment(final DeploymentRecord deployment) {
        final String success;
        final String failed;
        if (deployment.isEnabled()) {
            success = Console.MESSAGES.successDisabled(deployment.getRuntimeName());
            failed = Console.MESSAGES.failedToDisable(deployment.getRuntimeName());
        } else {
            success = Console.MESSAGES.successEnabled(deployment.getRuntimeName());
            failed = Console.MESSAGES.failedToEnable(deployment.getRuntimeName());
        }
        final PopupPanel loading = Feedback.loading(
                Console.CONSTANTS.common_label_plaseWait(),
                Console.CONSTANTS.common_label_requestProcessed(),
                () -> {});

        deploymentStore.enableDisableDeployment(deployment, new SimpleCallback<DMRResponse>() {
            @Override
            public void onFailure(final Throwable caught) {
                loading.hide();
                Console.error(failed, caught.getMessage());
            }

            @Override
            public void onSuccess(DMRResponse response) {
                loading.hide();
                ModelNode result = response.get();
                if (result.isFailure()) {
                    Console.error(failed, result.getFailureDescription());
                } else {
                    Console.info(success);
                }
                //                loadAssignmentsFor(deployment);
            }
        });
    }

    @Override
    public void updateDeployment(final DeploymentRecord deployment) {

    }

    @Override
    public void removeDeploymentFromGroup(final DeploymentRecord deployment) {
        deploymentStore.removeDeploymentFromGroup(deployment, new SimpleCallback<DMRResponse>() {
            @Override
            public void onSuccess(DMRResponse response) {
//                loadAssignmentsFor(deployment);
                DeploymentCommand.REMOVE_FROM_GROUP.displaySuccessMessage(DeploymentFinder.this, deployment);
            }

            @Override
            public void onFailure(Throwable t) {
                super.onFailure(t);
//                loadAssignmentsFor(deployment);
                DeploymentCommand.REMOVE_FROM_GROUP
                        .displayFailureMessage(DeploymentFinder.this, deployment, t);
            }
        });
    }

    @Override
    public void onRemoveContent(final DeploymentRecord deployment) {
        List<String> assignedGroups = contentRepository.getServerGroups(deployment);

        final PopupPanel loading = Feedback.loading(
                Console.CONSTANTS.common_label_plaseWait(),
                Console.CONSTANTS.common_label_requestProcessed(),
                () -> {});

        ModelNode operation = new ModelNode();
        operation.get(OP).set(COMPOSITE);
        operation.get(ADDRESS).setEmptyList();

        List<ModelNode> steps = new LinkedList<>();
        for (String group : assignedGroups) {
            ModelNode groupOp = new ModelNode();
            groupOp.get(OP).set(REMOVE);
            groupOp.get(ADDRESS).add("server-group", group);
            groupOp.get(ADDRESS).add("deployment", deployment.getName());
            steps.add(groupOp);
        }

        ModelNode removeContentOp = new ModelNode();
        removeContentOp.get(OP).set(REMOVE);
        removeContentOp.get(ADDRESS).add("deployment", deployment.getName());
        steps.add(removeContentOp);
        operation.get(STEPS).set(steps);
        dispatcher.execute(new DMRAction(operation), new SimpleCallback<DMRResponse>() {
            @Override
            public void onSuccess(DMRResponse dmrResponse) {
                loading.hide();

                ModelNode result = dmrResponse.get();

                if (result.isFailure()) {
                    Console.error(Console.MESSAGES.deletionFailed("Deployment " + deployment.getRuntimeName()),
                            result.getFailureDescription());
                } else {
                    Console.info(Console.MESSAGES.deleted("Deployment " + deployment.getRuntimeName()));
                }
//                refreshDeployments();
            }
        });
    }

    @Override
    public List<ServerGroupRecord> getPossibleGroupAssignments(final DeploymentRecord record) {
        return contentRepository.getPossibleServerGroupAssignments(record);
    }

    @Override
    public void launchGroupSelectionWizard(final DeploymentRecord record) {
        new ServerGroupSelector(this, record);
    }

    @Override
    public void onCreateUnmanaged(final DeploymentRecord entity) {

    }

    @Override
    public void refreshDeployments() {

    }

    public void launchNewDeploymentDialoge(DeploymentRecord record, boolean isUpdate) {
        launchDeploymentDialoge(Console.MESSAGES.createTitle("Deployment"), record, isUpdate);
    }

    public void launchDeploymentDialoge(String title, DeploymentRecord record, boolean isUpdate) {
        window = new DefaultWindow(title);
        window.setWidth(480);
        window.setHeight(480);
        window.trapWidget(
                new NewDeploymentWizard(this, window, isUpdate, record).asWidget());
        window.setGlassEnabled(true);
        window.center();
    }

    public void loadReference(String serverGroup, AsyncCallback<ServerInstance> callback) {

        getView().setReferenceServer(null);


        Outcome<FunctionContext> outcome = new Outcome<FunctionContext>() {
            @Override
            public void onFailure(final FunctionContext context) {
                Console.error("Unable to load deployment content", context.getErrorMessage()); // TODO i18n
            }

            @Override
            public void onSuccess(final FunctionContext context) {
                ServerInstance referenceServer = null;
                List<HostInfo> hosts = context.pop();
                for (Iterator<HostInfo> i = hosts.iterator(); i.hasNext() && referenceServer == null; ) {
                    HostInfo host = i.next();
                    List<ServerInstance> serverInstances = host.getServerInstances();
                    for (Iterator<ServerInstance> j = serverInstances.iterator();
                         j.hasNext() && referenceServer == null; ) {
                        ServerInstance server = j.next();
                        if (server.isRunning() && server.getGroup().equals(serverGroup)) {
                            referenceServer = server;
                        }
                    }
                }
                if (referenceServer != null) {

                    System.out.println("Found reference server " + referenceServer.getName() + " on " + referenceServer.getGroup() + " / " + referenceServer.getHost());
                    callback.onSuccess(referenceServer);

                } else {
                    callback.onFailure(new Throwable("no server found"));
                    //getView().noServerFound();
                }
            }
        };
        new Async<FunctionContext>().waterfall(new FunctionContext(), outcome,
                new TopologyFunctions.HostsAndGroups(dispatcher),
                new TopologyFunctions.ServerConfigs(dispatcher, beanFactory),
                new TopologyFunctions.RunningServerInstances(dispatcher));

    }

    public void loadDeployment(final DeploymentRecord deployment, ServerInstance referenceServer) {
        // TODO Should be replaced with a :read-resource(recursive=true)

        deploymentStore.loadDeployments(referenceServer, new AsyncCallback<List<DeploymentRecord>>() {
            @Override
            public void onFailure(final Throwable caught) {
                Console.error("Unable to load deployment content", caught.getMessage()); // TODO i18n
            }

            @Override
            public void onSuccess(final List<DeploymentRecord> result) {
                DeploymentRecord referenceDeployment = null;
                for (DeploymentRecord deploymentRecord : result) {
                    if (deploymentRecord.getName().equals(deployment.getName())) {
                        referenceDeployment = deploymentRecord;
                        break;
                    }
                }
                if (referenceDeployment != null) {
                    //                    getView().toggleSubdeployments(referenceDeployment.isHasSubdeployments());

                    getView().updateDeployment(referenceServer, referenceDeployment);

                   /* if (referenceDeployment.isHasSubdeployments()) {
                        deploymentStore.loadSubdeployments(referenceDeployment,
                                new AsyncCallback<List<DeploymentRecord>>() {
                                    @Override
                                    public void onFailure(final Throwable caught) {
                                        Console.error("Unable to load deployment content",
                                                caught.getMessage()); // TODO i18n
                                    }

                                    @Override
                                    public void onSuccess(final List<DeploymentRecord> result) {
                                        getView().updateSubdeployments(referenceServer, result);
                                    }
                                });
                    }
                    */
                }
            }
        });
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


    static class ServerGroupAssignment {

        final DeploymentRecord deployment;
        final String serverGroup;
        ServerInstance referenceServer;

        ServerGroupAssignment(final DeploymentRecord deployment, final String serverGroup) {
            this.deployment = deployment;
            this.serverGroup = serverGroup;
        }
    }

    @Override
    public void onClearActiveSelection(final ClearFinderSelectionEvent event) {
        getView().clearActiveSelection(event);
    }

    public void loadSubdeployments(ServerInstance referenceServer, DeploymentRecord deployment) {
        if (deployment.isHasSubdeployments()) {
            deploymentStore.loadSubdeployments(deployment,
                    new AsyncCallback<List<DeploymentRecord>>() {
                        @Override
                        public void onFailure(final Throwable caught) {
                            Console.error("Unable to load deployment content",
                                    caught.getMessage()); // TODO i18n
                        }

                        @Override
                        public void onSuccess(final List<DeploymentRecord> result) {
                            getView().updateSubdeployments(referenceServer, result);
                        }
                    });
        }
    }
}
