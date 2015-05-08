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

import org.jboss.dmr.client.ModelNode;

/**
 * A deployed and assigned content on a specific server.
 *
 * @author Harald Pehl
 */
public class Deployment extends Content {

    enum Status {
        OK, FAILED, STOPPED, UNDEFINED;
    }


    private final ReferenceServer referenceServer;

    public Deployment(final ReferenceServer referenceServer, final ModelNode node) {
        super(node);
        this.referenceServer = referenceServer;
    }

    public boolean isStandalone() {
        return referenceServer == ReferenceServer.STANDALONE;
    }

    public boolean isEnabled() {
        ModelNode enabled = get("enabled");
        //noinspection SimplifiableConditionalExpression
        return enabled.isDefined() ? enabled.asBoolean() : false;
    }

    public Status getStatus() {
        Status status = Status.UNDEFINED;
        ModelNode statusNode = get("status");
        if (statusNode.isDefined()) {
            try {
                Status.valueOf(statusNode.asString());
            } catch (IllegalArgumentException e) {
                // returns UNDEFINED
            }
        }
        return status;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Deployment {").append(getName());
        if (!isStandalone()) {
            builder.append("@").append(referenceServer.getHost()).append("/").append(referenceServer.getServer());
        }
        builder.append(", ")
                .append((isEnabled() ? "enabled" : "disabled"))
                .append(getStatus());
        builder.append("}");
        return builder.toString();
    }
}
