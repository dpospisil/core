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

import com.google.common.collect.Lists;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.LayoutPanel;
import com.google.gwt.user.client.ui.SplitLayoutPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.ProvidesKey;
import com.google.inject.Inject;
import org.jboss.as.console.client.Console;
import org.jboss.as.console.client.core.SuspendableViewImpl;
import org.jboss.as.console.client.domain.model.ServerGroupRecord;
import org.jboss.as.console.client.shared.util.Trim;
import org.jboss.as.console.client.widgets.nav.v3.ClearFinderSelectionEvent;
import org.jboss.as.console.client.widgets.nav.v3.ColumnManager;
import org.jboss.as.console.client.widgets.nav.v3.FinderColumn;
import org.jboss.as.console.client.widgets.nav.v3.MenuDelegate;
import org.jboss.gwt.circuit.Dispatcher;

/**
 * @author Harald Pehl
 */
public class DeploymentFinderView extends SuspendableViewImpl
        implements DeploymentFinder.MyView {

    interface Template extends SafeHtmlTemplates {

        @Template("<div class=\"{0}\">{1}</div>")
        SafeHtml item(String cssClass, String name);

        @Template("<div class=\"{0}\" title=\"{1}\">{2}</div>")
        SafeHtml trimmedItem(String cssClass, String trimmed, String full);

        @Template("<div class=\"{0}\" title=\"{1}\">{2}<br/><span styles=\"font-size:10px;font-weight:italic\">{3} / {4}</span></div>")
        SafeHtml deploymentItem(String cssClass, String trimmed, String full, String host, String server);

        @Template("<div class=\"{0}\" title=\"{1}\">{2}</div>")
        SafeHtml subDeploymentItem(String cssClass, String trimmed, String full);

        @Template("<div class='preview-content'><h2>{0}</h2>" +
                "<ul>" +
                "<li>Runtime Name: {1}</li>" +
                "<li>Enabled: {2}</li>" +
                "</ul>" +
                "</div>")
        SafeHtml assignmentPreview(String name, String runtimeName, boolean enabled);

        @Template("<div class='preview-content'><h2>{0}</h2>" +
                "<ul>" +
                "<li>Runtime Name: {1}</li>" +
                "<li>Enabled: {2}</li>" +
                "<li>Host: {3}</li>" +
                "<li>Server: {4}</li>" +
                "</ul>" +
                "</div>")
        SafeHtml deploymentPreview(String name, String runtimeName, String host, String server);

        @Template("<div class='preview-content'><h2>{0}</h2>" +
                "<ul>" +
                "<li>Runtime Name: {1}</li>" +
                "<li>Enabled: {2}</li>" +
                "</ul>" +
                "</div>")
        SafeHtml subDeploymentPreview(String name, String runtimeName);
    }


    private static final Template TEMPLATE = GWT.create(Template.class);

    private SplitLayoutPanel layout;
    private LayoutPanel contentCanvas;
    private ColumnManager columnManager;

    private FinderColumn<ServerGroupRecord> serverGroupColumn;
    private Widget serverGroupColumnWidget;
    private FinderColumn<Assignment> assignmentColumn;
    private Widget assignmentColumnWidget;
    private FinderColumn<Deployment> deploymentColumn;
    private Widget deploymentColumnWidget;
    private FinderColumn<Deployment> subdeploymentColumn;
    private Widget subdeploymentColumnWidget;

    @Inject
    @SuppressWarnings("unchecked")
    public DeploymentFinderView(Dispatcher circuit, final DeploymentStore deploymentStore) {

        // ------------------------------------------------------ server group

        serverGroupColumn = new FinderColumn<>(
                FinderColumn.FinderId.DEPLOYMENT,
                "Server Group",
                new FinderColumn.Display<ServerGroupRecord>() {

                    @Override
                    public boolean isFolder(ServerGroupRecord data) {
                        return true;
                    }

                    @Override
                    public SafeHtml render(String baseCss, ServerGroupRecord data) {
                        return TEMPLATE.item(baseCss, data.getName());
                    }

                    @Override
                    public String rowCss(ServerGroupRecord data) {
                        return "";
                    }
                },
                new ProvidesKey<ServerGroupRecord>() {
                    @Override
                    public Object getKey(ServerGroupRecord item) {
                        return item.getName();
                    }
                });

        serverGroupColumn.setShowSize(true);

        serverGroupColumn.addSelectionChangeHandler(event -> {
            columnManager.reduceColumnsTo(1);
            if (serverGroupColumn.hasSelectedItem()) {
                columnManager.updateActiveSelection(serverGroupColumnWidget);
                columnManager.appendColumn(assignmentColumnWidget);
                circuit.dispatch(new LoadAssignments(serverGroupColumn.getSelectedItem().getName()));
            }
        });


        // ------------------------------------------------------ assignments

        assignmentColumn = new FinderColumn<>(
                FinderColumn.FinderId.DEPLOYMENT,
                "Assignment",
                new FinderColumn.Display<Assignment>() {
                    @Override
                    public boolean isFolder(final Assignment data) {
                        return hasReferenceServer(data);
                    }

                    @Override
                    public SafeHtml render(final String baseCss, final Assignment data) {
                        return TEMPLATE.trimmedItem(baseCss, Trim.abbreviateMiddle(data.getName(), 20), data.getName());
                    }

                    @Override
                    public String rowCss(final Assignment data) {
                        return hasReferenceServer(data) ? "active" : "inactive";
                    }

                    private boolean hasReferenceServer(final Assignment data) {
                        return deploymentStore.getReferenceServer(data.getServerGroup()) != null;
                    }

                },
                new ProvidesKey<Assignment>() {
                    @Override
                    public Object getKey(final Assignment item) {
                        return item.getName();
                    }
                }
        );

        assignmentColumn.setTopMenuItems(new MenuDelegate<>("Add", item -> Console.warning("Not yet implemented")));

        assignmentColumn.setMenuItems(new MenuDelegate<>("Disable", item -> Console.warning("Not yet implemented")));

        assignmentColumn.setPreviewFactory((data, callback) ->
                callback.onSuccess(
                        TEMPLATE.assignmentPreview(data.getName(), data.getRuntimeName(), data.isEnabled())));

        assignmentColumn.addSelectionChangeHandler(selectionChangeEvent -> {
            columnManager.reduceColumnsTo(2);
            if (assignmentColumn.hasSelectedItem()) {
                columnManager.updateActiveSelection(assignmentColumnWidget);
                Assignment assignment = assignmentColumn.getSelectedItem();
                ReferenceServer referenceServer = deploymentStore.getReferenceServer(assignment.getServerGroup());
                if (referenceServer != null) {
                    circuit.dispatch(new LoadDeployments(referenceServer));
                }
            }
        });


        // ------------------------------------------------------ deployments

        deploymentColumn = new FinderColumn<>(
                FinderColumn.FinderId.DEPLOYMENT,
                "Deployment",
                new FinderColumn.Display<Deployment>() {
                    @Override
                    public boolean isFolder(final Deployment data) {
                        return data.hasSubDeployments();
                    }

                    @Override
                    public SafeHtml render(final String baseCss, final Deployment data) {
                        return TEMPLATE.deploymentItem(baseCss,
                                Trim.abbreviateMiddle(data.getName(), 20), data.getName(),
                                data.getReferenceServer().getHost(), data.getReferenceServer().getServer());
                    }

                    @Override
                    public String rowCss(final Deployment data) {
                        return "";
                    }
                },
                new ProvidesKey<Deployment>() {
                    @Override
                    public Object getKey(final Deployment item) {
                        return item.getName();
                    }
                }
        );

        deploymentColumn.setShowSize(true);

        deploymentColumn.setPreviewFactory((data, callback) ->
                callback.onSuccess(TEMPLATE.deploymentPreview(data.getName(), data.getRuntimeName(),
                        data.getReferenceServer().getHost(), data.getReferenceServer().getServer())));

        deploymentColumn.addSelectionChangeHandler(event -> {
            columnManager.reduceColumnsTo(3);
            if (deploymentColumn.hasSelectedItem()) {
                columnManager.updateActiveSelection(deploymentColumnWidget);
                Deployment deployment = deploymentColumn.getSelectedItem();
                if (deployment.hasSubDeployments()) {
                    columnManager.appendColumn(subdeploymentColumnWidget);
                    // TODO update sub deployments
                }
            }
        });


        // ------------------------------------------------------ sub deployments

        subdeploymentColumn = new FinderColumn<>(
                FinderColumn.FinderId.DEPLOYMENT,
                "Subdeployment",
                new FinderColumn.Display<Deployment>() {
                    @Override
                    public boolean isFolder(final Deployment data) {
                        return false;
                    }

                    @Override
                    public SafeHtml render(final String baseCss, final Deployment data) {
                        return TEMPLATE.subDeploymentItem(baseCss,
                                Trim.abbreviateMiddle(data.getName(), 20), data.getName());
                    }

                    @Override
                    public String rowCss(final Deployment data) {
                        return "";
                    }
                },
                new ProvidesKey<Deployment>() {
                    @Override
                    public Object getKey(final Deployment item) {
                        return item.getName();
                    }
                }
        );

        subdeploymentColumn.setShowSize(true);

        subdeploymentColumn.setPreviewFactory((data, callback) ->
                callback.onSuccess(TEMPLATE.subDeploymentPreview(data.getName(), data.getRuntimeName())));

        subdeploymentColumn.addSelectionChangeHandler(event -> {
            columnManager.reduceColumnsTo(4);
            if (subdeploymentColumn.hasSelectedItem()) {
                columnManager.updateActiveSelection(subdeploymentColumnWidget);
            }
        });


        // setup UI
        serverGroupColumnWidget = serverGroupColumn.asWidget();
        assignmentColumnWidget = assignmentColumn.asWidget();
        deploymentColumnWidget = deploymentColumn.asWidget();
        subdeploymentColumnWidget = subdeploymentColumn.asWidget();

        contentCanvas = new LayoutPanel();
        layout = new SplitLayoutPanel(2);

        columnManager = new ColumnManager(layout);
        columnManager.addWest(serverGroupColumnWidget);
        columnManager.addWest(assignmentColumnWidget);
        columnManager.addWest(deploymentColumnWidget);
        columnManager.addWest(subdeploymentColumnWidget);
        columnManager.add(contentCanvas);
        columnManager.setInitialVisible(1);
    }

    @Override
    public Widget createWidget() {
        return layout;
    }


    // ------------------------------------------------------ update columns

    @Override
    public void updateServerGroups(final Iterable<ServerGroupRecord> serverGroups) {
        serverGroupColumn.updateFrom(Lists.newArrayList(serverGroups));
    }

    @Override
    public void updateAssignments(final Iterable<Assignment> assignments) {
        assignmentColumn.updateFrom(Lists.newArrayList(assignments));
    }

    @Override
    public void updateDeployments(Iterable<Deployment> deployments) {
        deploymentColumn.updateFrom(Lists.newArrayList(deployments));
    }

    @Override
    public void updateSubdeployments(Iterable<Deployment> subDeployments) {
    }


    // ------------------------------------------------------ slot management

    @Override
    public void setInSlot(final Object slot, final IsWidget content) {
        if (slot == DeploymentFinder.TYPE_MainContent) {
            if (content != null) { setContent(content); } else { contentCanvas.clear(); }
        }
    }

    private void setContent(IsWidget newContent) {
        contentCanvas.clear();
        contentCanvas.add(newContent);
    }


    // ------------------------------------------------------ finder related methods

    @Override
    public void setPreview(final SafeHtml html) {
        //        if (contentCanvas.getWidgetCount() == 0) {
        Scheduler.get().scheduleDeferred(() -> {
            contentCanvas.clear();
            contentCanvas.add(new HTML(html));
        });
        //        }
    }

    @Override
    public void toggleScrolling(final boolean enforceScrolling, final int requiredWidth) {
        columnManager.toogleScrolling(enforceScrolling, requiredWidth);
    }

    public void clearActiveSelection(final ClearFinderSelectionEvent event) {
        serverGroupColumnWidget.getElement().removeClassName("active");
        assignmentColumnWidget.getElement().removeClassName("active");
        deploymentColumnWidget.getElement().removeClassName("active");
        subdeploymentColumnWidget.getElement().removeClassName("active");
    }
}
