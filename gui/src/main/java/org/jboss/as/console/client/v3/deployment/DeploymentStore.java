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

import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.inject.Inject;
import org.jboss.as.console.client.domain.model.ServerInstance;
import org.jboss.as.console.client.domain.topology.HostInfo;
import org.jboss.as.console.client.domain.topology.TopologyFunctions;
import org.jboss.as.console.client.shared.BeanFactory;
import org.jboss.as.console.client.shared.flow.FunctionContext;
import org.jboss.as.console.client.v3.dmr.Operation;
import org.jboss.as.console.client.v3.dmr.ResourceAddress;
import org.jboss.dmr.client.ModelNode;
import org.jboss.dmr.client.Property;
import org.jboss.dmr.client.dispatch.DispatchAsync;
import org.jboss.dmr.client.dispatch.impl.DMRAction;
import org.jboss.dmr.client.dispatch.impl.DMRResponse;
import org.jboss.gwt.circuit.ChangeSupport;
import org.jboss.gwt.circuit.Dispatcher;
import org.jboss.gwt.circuit.meta.Process;
import org.jboss.gwt.circuit.meta.Store;
import org.jboss.gwt.flow.client.Async;
import org.jboss.gwt.flow.client.Outcome;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.jboss.dmr.client.ModelDescriptionConstants.INCLUDE_RUNTIME;
import static org.jboss.dmr.client.ModelDescriptionConstants.READ_CHILDREN_RESOURCES_OPERATION;
import static org.jboss.dmr.client.ModelDescriptionConstants.RECURSIVE;

/**
 * Circuit store for deployments.
 * <p>
 * Please note that the store's state is only valid in the context of the deployment finder. It's not guaranteed that
 * the state is valid throughout the complete lifecycle of the management console. Other management clients can upload,
 * assign and (un)deploy applications at any time w/o this store is aware of.
 * <p>
 * This might change one we have server side notifications. But in the meantime you should use the
 * {@link ResetDeploymentStore} action to reset the store's state before loading fresh state.
 *
 * @author Harald Pehl
 */
@Store
public class DeploymentStore extends ChangeSupport {

    private final DispatchAsync dispatcher;
    private final BeanFactory beanFactory;

    private final List<Content> repository;
    private final Set<Assignment> assignments; // for server group specified in LoadAssignments
    private final Set<Deployment> deployments; // for reference server specified in LoadDeployments
    private final Map<String, ReferenceServer> referenceServers; // key == server group

    @Inject
    public DeploymentStore(final DispatchAsync dispatcher, final BeanFactory beanFactory) {
        this.dispatcher = dispatcher;
        this.beanFactory = beanFactory;

        this.repository = new ArrayList<>();
        this.assignments = new HashSet<>();
        this.deployments = new HashSet<>();
        this.referenceServers = new HashMap<>();
    }

    @Process(actionType = ResetDeploymentStore.class)
    public void reset(final Dispatcher.Channel channel) {
        repository.clear();
        assignments.clear();
        deployments.clear();
        channel.ack();
    }

    @Process(actionType = LoadRepository.class)
    public void loadRepository(final Dispatcher.Channel channel) {
        Operation op = new Operation.Builder(READ_CHILDREN_RESOURCES_OPERATION, ResourceAddress.ROOT).build();

        dispatcher.execute(new DMRAction(op), new AsyncCallback<DMRResponse>() {
            @Override
            public void onFailure(final Throwable caught) {
                channel.nack(caught);
            }

            @Override
            public void onSuccess(final DMRResponse response) {
                ModelNode result = response.get();
                if (result.isFailure()) {
                    channel.nack(result.getFailureDescription());
                } else {
                    List<Property> properties = result.asPropertyList();
                    for (Property property : properties) {
                        repository.add(new Content(property.getValue()));
                    }
                    channel.ack();
                }
            }
        });
    }

    @Process(actionType = LoadAssignments.class)
    public void loadAssignments(final LoadAssignments action, final Dispatcher.Channel channel) {
        final String serverGroup = action.getServerGroup();
        ResourceAddress address = new ResourceAddress().add("server-group", serverGroup);
        Operation op = new Operation.Builder(READ_CHILDREN_RESOURCES_OPERATION, address).build();

        dispatcher.execute(new DMRAction(op), new AsyncCallback<DMRResponse>() {
            @Override
            public void onFailure(final Throwable caught) {
                channel.nack(caught);
            }

            @Override
            public void onSuccess(final DMRResponse response) {
                ModelNode result = response.get();
                if (result.isFailure()) {
                    channel.nack(result.getFailureDescription());
                } else {
                    assignments.clear();
                    List<Property> properties = result.asPropertyList();
                    for (Property property : properties) {
                        assignments.add(new Assignment(serverGroup, property.getValue()));
                    }
                    findReferenceServer(serverGroup, channel);
                }
            }
        });
    }

    private void findReferenceServer(final String serverGroup, final Dispatcher.Channel channel) {
        Outcome<FunctionContext> outcome = new Outcome<FunctionContext>() {
            @Override
            public void onFailure(final FunctionContext context) {
                channel.nack(context.getError());
            }

            @Override
            public void onSuccess(final FunctionContext context) {
                referenceServers.remove(serverGroup);
                ReferenceServer referenceServer = null;
                List<HostInfo> hosts = context.pop();
                for (Iterator<HostInfo> i = hosts.iterator(); i.hasNext() && referenceServer == null; ) {
                    HostInfo host = i.next();
                    List<ServerInstance> serverInstances = host.getServerInstances();
                    for (Iterator<ServerInstance> j = serverInstances.iterator();
                            j.hasNext() && referenceServer == null; ) {
                        ServerInstance server = j.next();
                        if (server.isRunning() && server.getGroup().equals(serverGroup)) {
                            referenceServer = new ReferenceServer(server.getHost(), server.getName());
                        }
                    }
                }
                if (referenceServer != null) {
                    referenceServers.put(serverGroup, referenceServer);
                }
                channel.ack();
            }
        };
        new Async<FunctionContext>().waterfall(new FunctionContext(), outcome,
                new TopologyFunctions.HostsAndGroups(dispatcher),
                new TopologyFunctions.ServerConfigs(dispatcher, beanFactory),
                new TopologyFunctions.RunningServerInstances(dispatcher));
    }

    @Process(actionType = LoadDeployments.class)
    public void loadDeployments(final LoadDeployments action, final Dispatcher.Channel channel) {
        ResourceAddress address = action.getReferenceServer().getAddress();
        Operation op = new Operation.Builder(READ_CHILDREN_RESOURCES_OPERATION, address)
                .param(INCLUDE_RUNTIME, true)
                .param(RECURSIVE, true)
                .build();

        dispatcher.execute(new DMRAction(op), new AsyncCallback<DMRResponse>() {
            @Override
            public void onFailure(final Throwable caught) {
                channel.nack(caught);
            }

            @Override
            public void onSuccess(final DMRResponse response) {
                ModelNode result = response.get();
                if (result.isFailure()) {
                    channel.nack(result.getFailureDescription());
                } else {
                    deployments.clear();
                    List<Property> properties = result.asPropertyList();
                    for (Property property : properties) {
                        deployments.add(new Deployment(action.getReferenceServer(), property.getValue()));
                    }
                    channel.ack();
                }
            }
        });
    }


    // ------------------------------------------------------ state access

    /**
     * @return the uploaded deployment content
     */
    public List<Content> getRepository() {
        return repository;
    }

    /**
     * @return the running reference server for the specified server group or {@code null} if no reference server is
     * available for that server group
     */
    public ReferenceServer getReferenceServer(String serverGroup) {
        return referenceServers.get(serverGroup);
    }

    /**
     * @return the assignments for the server group specified in the related {@link LoadAssignments} action
     */
    public Set<Assignment> getAssignments() {
        return assignments;
    }

    /**
     * @return the deployments for the reference server specified in the related {@link LoadDeployments} action
     */
    public Set<Deployment> getDeployments() {
        return deployments;
    }
}
