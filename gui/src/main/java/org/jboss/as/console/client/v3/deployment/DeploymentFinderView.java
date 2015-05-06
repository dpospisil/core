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
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.LayoutPanel;
import com.google.gwt.user.client.ui.SplitLayoutPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.ProvidesKey;
import com.google.gwt.view.client.SelectionChangeEvent;
import com.google.inject.Inject;
import org.jboss.as.console.client.core.SuspendableViewImpl;
import org.jboss.as.console.client.domain.model.ServerGroupRecord;
import org.jboss.as.console.client.domain.model.ServerInstance;
import org.jboss.as.console.client.shared.deployment.model.DeploymentRecord;
import org.jboss.as.console.client.shared.util.Trim;
import org.jboss.as.console.client.widgets.nav.v3.ClearFinderSelectionEvent;
import org.jboss.as.console.client.widgets.nav.v3.ColumnManager;
import org.jboss.as.console.client.widgets.nav.v3.ContextualCommand;
import org.jboss.as.console.client.widgets.nav.v3.FinderColumn;
import org.jboss.as.console.client.widgets.nav.v3.MenuDelegate;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Harald Pehl
 */
public class DeploymentFinderView extends SuspendableViewImpl
        implements DeploymentFinder.MyView {

    private ServerInstance referenceServer;

    interface Template extends SafeHtmlTemplates {

        @Template("<div class=\"{0}\" title='{2}'>{1}</div>")
        SafeHtml item(String cssClass, String shortName, String fullName);

       /* @Template("<div class='preview-content'><h2>{0}</h2>" +
                "<ul>" +
                "<li>Runtime Name: {1}</li>" +
                "</ul>" +
                "</div>")
        SafeHtml content(String name, String runtimeName);

        @Template("<div class='preview-content'><h2>{0}</h2>" +
                "<ul>" +
                "<li>Runtime Name: {1}</li>" +
                "<li>Enabled: {2}</li>" +
                "</ul>" +
                "</div>")
        SafeHtml assignment(String name, String runtimeName, boolean enabled);*/

        @Template("<div class=\"{0}\" title='{1}'>{1}<br/><span styles='font-size:10px;font-weight:italic'>{2}/{3}</span></div>")
        SafeHtml deployment(String name, String runtimeName, String host, String server);
    }


    private static final Template TEMPLATE = GWT.create(Template.class);

    private DeploymentFinder presenter;
    private boolean hasSubdeployments;

    private SplitLayoutPanel layout;
    private LayoutPanel contentCanvas;
    private ColumnManager columnManager;

    private FinderColumn<ServerGroupRecord> serverGroupColumn;
    private Widget serverGroupColumnWidget;
    private FinderColumn<DeploymentRecord> deploymentColumn;
    private Widget deploymentColumnWidget;
    private FinderColumn<DeploymentRecord> subdeploymentColumn;
    private Widget subdeploymentColumnWidget;

    private FinderColumn<DeploymentRecord> assignmentColumn;
    private Widget assignmentColumnWidget;


    @Inject
    @SuppressWarnings("unchecked")
    public DeploymentFinderView() {

        // ------------------------------------------------------ server group

        serverGroupColumn = new FinderColumn<ServerGroupRecord>(
                FinderColumn.FinderId.DEPLOYMENT,
                "Server Group",
                new FinderColumn.Display<ServerGroupRecord>() {

                    @Override
                    public boolean isFolder(ServerGroupRecord data) {
                        return true;
                    }

                    @Override
                    public SafeHtml render(String baseCss, ServerGroupRecord data) {
                        return TEMPLATE.item(baseCss, Trim.abbreviateMiddle(data.getName(), 20), data.getName());
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

        serverGroupColumn.addSelectionChangeHandler(new SelectionChangeEvent.Handler() {
            @Override
            public void onSelectionChange(final SelectionChangeEvent event) {
                columnManager.reduceColumnsTo(1);
                if (serverGroupColumn.hasSelectedItem()) {
                    columnManager.updateActiveSelection(serverGroupColumnWidget);
                    columnManager.appendColumn(assignmentColumnWidget);
                    presenter.loadAssignmentsFor(serverGroupColumn.getSelectedItem());
                }
            }
        });

        // ------------------------------------------------------ sub deployments

        subdeploymentColumn = new FinderColumn<>(
                FinderColumn.FinderId.DEPLOYMENT,
                "Subdeployment",
                new FinderColumn.Display<DeploymentRecord>() {
                    @Override
                    public boolean isFolder(final DeploymentRecord data) {
                        return false;
                    }

                    @Override
                    public SafeHtml render(final String baseCss, final DeploymentRecord data) {
                        return TEMPLATE.item(baseCss, Trim.abbreviateMiddle(data.getName(), 20), data.getName());
                    }

                    @Override
                    public String rowCss(final DeploymentRecord data) {
                        return "";
                    }
                },
                new ProvidesKey<DeploymentRecord>() {
                    @Override
                    public Object getKey(final DeploymentRecord item) {
                        return item.getName();
                    }
                }
        );

        subdeploymentColumn.setShowSize(true);

        /*subdeploymentColumn.setPreviewFactory((data, callback) ->
                callback.onSuccess(TEMPLATE.deployment(data.getName(), data.getRuntimeName())));
*/
        subdeploymentColumn.addSelectionChangeHandler(
                event -> {
                    columnManager.reduceColumnsTo(4);
                    if (subdeploymentColumn.hasSelectedItem()) {
                        columnManager.updateActiveSelection(subdeploymentColumnWidget);
                    }
                });

        // ------------------------------------------------------ deployments

        deploymentColumn = new FinderColumn<>(
                FinderColumn.FinderId.DEPLOYMENT,
                "Deployment",
                new FinderColumn.Display<DeploymentRecord>() {
                    @Override
                    public boolean isFolder(final DeploymentRecord data) {
                        return data.isHasSubdeployments();
                    }

                    @Override
                    public SafeHtml render(final String baseCss, final DeploymentRecord data) {
                        return TEMPLATE.deployment(baseCss,
                                Trim.abbreviateMiddle(data.getName(), 20),
                                referenceServer.getHost(),
                                referenceServer.getName()
                        );
                    }

                    @Override
                    public String rowCss(final DeploymentRecord data) {
                        return "";
                    }
                },
                new ProvidesKey<DeploymentRecord>() {
                    @Override
                    public Object getKey(final DeploymentRecord item) {
                        return item.getName();
                    }
                }
        );



        //deploymentColumn.setShowSize(true);

        /*deploymentColumn.setPreviewFactory((data, callback) ->
                callback.onSuccess(TEMPLATE.deployment(data.getName(), data.getRuntimeName())));*/

        deploymentColumn.addSelectionChangeHandler(
                event -> {
                    columnManager.reduceColumnsTo(3);
                    if(deploymentColumn.hasSelectedItem()) {
                        columnManager.updateActiveSelection(deploymentColumnWidget);

                        DeploymentRecord selectedItem = deploymentColumn.getSelectedItem();

                        columnManager.appendColumn(subdeploymentColumnWidget);
                        presenter.loadSubdeployments(referenceServer, selectedItem);

                    }
                });



        // -----

        assignmentColumn = new FinderColumn<>(
                FinderColumn.FinderId.DEPLOYMENT,
                "Assignment",
                new FinderColumn.Display<DeploymentRecord>() {
                    @Override
                    public boolean isFolder(final DeploymentRecord data) {
                        return referenceServer!=null;
                    }

                    @Override
                    public SafeHtml render(final String baseCss, final DeploymentRecord data) {
                        return TEMPLATE.item(baseCss, Trim.abbreviateMiddle(data.getName(), 20), data.getName());
                    }

                    @Override
                    public String rowCss(final DeploymentRecord data) {
                        return referenceServer!=null ? "active" : "inactive";
                    }
                },
                new ProvidesKey<DeploymentRecord>() {
                    @Override
                    public Object getKey(final DeploymentRecord item) {
                        return item.getName();
                    }
                }
        );

        assignmentColumn.setTopMenuItems(new MenuDelegate<DeploymentRecord>(
                "Add", new ContextualCommand<DeploymentRecord>() {
            @Override
            public void executeOn(DeploymentRecord item) {

            }
        }
        ));

        assignmentColumn.setMenuItems(
                new MenuDelegate<DeploymentRecord>("Disable", new ContextualCommand<DeploymentRecord>() {
                    @Override
                    public void executeOn(DeploymentRecord item) {

                    }
                })
        );


        assignmentColumn.addSelectionChangeHandler(new SelectionChangeEvent.Handler() {
            @Override
            public void onSelectionChange(SelectionChangeEvent selectionChangeEvent) {
                columnManager.reduceColumnsTo(2);
                if(assignmentColumn.hasSelectedItem())
                {
                        columnManager.updateActiveSelection(assignmentColumnWidget);

                    if(referenceServer!=null) {
                        presenter.loadDeployment(
                                assignmentColumn.getSelectedItem(),
                                referenceServer
                        );
                    }

                }
            }
        });
        // ----

        // setup UI
        serverGroupColumnWidget = serverGroupColumn.asWidget();
        deploymentColumnWidget = deploymentColumn.asWidget();
        subdeploymentColumnWidget = subdeploymentColumn.asWidget();
        assignmentColumnWidget = assignmentColumn.asWidget();

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


    @Override
    public void setPresenter(final DeploymentFinder presenter) {
        this.presenter = presenter;
    }


    // ------------------------------------------------------ deployment related methods


    @Override
    public void updateServerGroups(final List<ServerGroupRecord> serverGroups) {
        serverGroupColumn.updateFrom(serverGroups);
    }



    @Override
    public void updateAssignments(final List<DeploymentRecord> deployments) {
        assignmentColumn.updateFrom(deployments);
    }

    @Override
    public void updateDeployment(ServerInstance referenceServer, final DeploymentRecord deployment) {

        ArrayList<DeploymentRecord> records = new ArrayList<DeploymentRecord>();
        records.add(deployment);
        deploymentColumn.updateFrom(records);

        columnManager.appendColumn(deploymentColumnWidget);
    }

    @Override
    public void updateSubdeployments(ServerInstance referenceServer, final List<DeploymentRecord> subdeployments) {

        subdeploymentColumn.updateFrom(subdeployments);
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

    private void clearNestedPresenter() {
        presenter.clearSlot(DeploymentFinder.TYPE_MainContent);
        Scheduler.get().scheduleDeferred(() -> {
            if (presenter.getPlaceManager().getHierarchyDepth() > 1) {
                presenter.getPlaceManager().revealRelativePlace(1);
            }
        });
    }

    @Override
    public void setReferenceServer(ServerInstance server) {

        referenceServer = server;
    }

    @Override
    public void noServerFound() {
        columnManager.reduceColumnsTo(2);
    }
}
