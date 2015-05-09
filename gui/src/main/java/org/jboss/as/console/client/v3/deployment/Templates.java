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

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;

/**
 * @author Harald Pehl
 */
final class Templates {

    static final Items ITEMS = GWT.create(Items.class);
    static final Previews PREVIEWS = GWT.create(Previews.class);


    interface Items extends SafeHtmlTemplates {

        @Template("<div class=\"{0}\">{1}</div>")
        SafeHtml full(String cssClass, String name);

        @Template("<div class=\"{0}\" title=\"{1}\">{2}</div>")
        SafeHtml trimmed(String cssClass, String full, String trimmed);

        @Template("<div class=\"{0}\">Host: {1}<br/>Server: {2}</div>")
        SafeHtml deployment(String cssClass, String host, String server);

        @Template("<div class=\"{0}\" title=\"{1}\">{2}</div>")
        SafeHtml subdeployment(String cssClass, String full, String trimmed);
    }


    interface Previews extends SafeHtmlTemplates {

        @Template("<div class='preview-content'><h2>{0}</h2>" +
                "<ul>" +
                "<li>{1}</li>" +
                "<li>{2}</li>" +
                "</ul>" +
                "</div>")
        SafeHtml assignment(String name, String enabledDisabled, String referenceServerInfo);

        @Template("<div class='preview-content'><h2>{0}</h2>" +
                "<ul>" +
                "<li>Runtime Name: {1}</li>" +
                "<li>Host: {2}</li>" +
                "<li>Server: {3}</li>" +
                "</ul>" +
                "</div>")
        SafeHtml deployment(String name, String runtimeName, String host, String server);

        @Template("<div class='preview-content'><h2>{0}</h2></div>")
        SafeHtml subdeployment(String name);
    }
}
