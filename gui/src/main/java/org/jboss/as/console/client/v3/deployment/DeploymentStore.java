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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.gwt.user.client.rpc.AsyncCallback;
import org.jboss.as.console.client.core.BootstrapContext;
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

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.jboss.dmr.client.ModelDescriptionConstants.READ_CHILDREN_RESOURCES_OPERATION;

/**
 * Circuit store for deployments.
 * <p>
 * Please note that the store's state is only valid in the context of the deployment finder. It's not guaranteed that
 * the state is valid throughout the complete lifecycle of the management console. Other management clients can upload,
 * assign and (un)deploy applications at any time w/o this store is aware of.
 * <p>
 * This might change one we have server side notifications. But in the meantime you should use the {@link Reset} action
 * to reset the store's state before loading fresh state.
 *
 * @author Harald Pehl
 */
@Store
public class DeploymentStore extends ChangeSupport {

    private final DispatchAsync dispatcher;
    private final BootstrapContext bootstrapContext;

    private final List<Content> repository;
    private final Multimap<String, Assignment> assignments;
    private final Multimap<ReferenceServer, Deployment> deployments;

    @Inject
    public DeploymentStore(final DispatchAsync dispatcher,
            final BootstrapContext bootstrapContext) {
        this.dispatcher = dispatcher;
        this.bootstrapContext = bootstrapContext;

        this.repository = new ArrayList<>();
        this.assignments = ArrayListMultimap.create();
        this.deployments = ArrayListMultimap.create();
    }

    @Process(actionType = Reset.class)
    public void reset(Dispatcher.Channel channel) {
        repository.clear();
        assignments.clear();
        deployments.clear();
        channel.ack();
    }

    @Process(actionType = LoadRepository.class)
    public void loadRepository(Dispatcher.Channel channel) {
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
    public void loadAssignments(LoadAssignments action, Dispatcher.Channel channel) {
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
                        assignments.put(serverGroup, new Assignment(serverGroup, property.getValue()));
                    }
                    channel.ack();
                }
            }
        });
    }

    @Process(actionType = LoadDeployments.class)
    public void loadDeployments(LoadDeployments action, Dispatcher.Channel channel) {
        final ReferenceServer referenceServer = action.getReferenceServer();
        ResourceAddress address = referenceServer.getAddress();
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
                    deployments.clear();
                    List<Property> properties = result.asPropertyList();
                    for (Property property : properties) {
                        deployments.put(referenceServer, new Deployment(referenceServer, property.getValue()));
                    }
                    channel.ack();
                }
            }
        });
    }


    private boolean isStandalone() {
        return bootstrapContext.isStandalone();
    }

    private boolean isDomain() {
        return !bootstrapContext.isStandalone();
    }


    // ------------------------------------------------------ state access

    public List<Content> getRepository() {
        return repository;
    }

    public Collection<Assignment> getAssignments(String serverGroup) {
        return assignments.get(serverGroup);
    }

    public Collection<Deployment> getDeployments(ReferenceServer referenceServer) {
        return deployments.get(referenceServer);
    }
}
