package org.useware.kernel.gui.behaviour;

import org.useware.kernel.model.scopes.DefaultActivation;
import org.useware.kernel.model.Dialog;
import org.useware.kernel.model.mapping.Node;
import org.useware.kernel.model.mapping.NodePredicate;
import org.useware.kernel.model.scopes.Scope;
import org.useware.kernel.model.structure.QName;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A registry for dialog statements. It reflects the current dialog state.
 *
 * @author Heiko Braun
 * @date 1/22/13
 */
public class DialogState {

    private Dialog dialog;
    private final StatementContext externalContext;
    private Map<Integer, MutableContext> scope2context;

    /**
     * Maps the active units under their parent (!) scope
     */
    private Map<Integer, QName> activeInScope = new HashMap<Integer, QName>();

    public DialogState(Dialog dialog, StatementContext parentContext) {
        this.dialog = dialog;
        this.externalContext = parentContext;
        this.scope2context = new HashMap<Integer, MutableContext>();

        resetActivation();
    }

    public void resetActivation() {

        activeInScope.clear();

        DefaultActivation activation = new DefaultActivation();
        dialog.getInterfaceModel().accept(activation);
        for(QName unitId : activation.getActiveItems().values())
        {
            int scopeId = getScopeId(unitId);
            activeInScope.put(scopeId, unitId);
        }
    }

    public void clearStatement(QName sourceId, String key) {
        ((MutableContext)getContext(sourceId)).clearStatement(key);
    }

    public void setStatement(QName interactionUnitId, String key, String value) {
        MutableContext context = (MutableContext) getContext(interactionUnitId);
        assert context!=null : "No context for " + interactionUnitId;

        System.out.println(">> Set '"+key+"' on scope ["+context.getScopeId()+"]: "+value);
        context.setStatement(key, value);
    }

    public StatementContext getContext(QName interactionUnitId) {

        final Node<Scope> self = dialog.getScopeModel().findNode(interactionUnitId);
        assert self!=null : "Unit not present in shim: "+ interactionUnitId;

        Scope scope = self.getData();

        // lazy initialisation
        if(!scope2context.containsKey(scope.getScopeId()))
        {

            // extract parent scopes

            List<Node<Scope>> parentScopeNodes = self.collectParents(new NodePredicate<Scope>() {
                Set<Integer> tracked = new HashSet<Integer>();

                @Override
                public boolean appliesTo(Node<Scope> candidate) {
                    if (self.getData().getScopeId() != candidate.getData().getScopeId()) {
                        if (!tracked.contains(candidate.getData().getScopeId())) {
                            tracked.add(candidate.getData().getScopeId());
                            return true;
                        }

                        return false;
                    }

                    return false;
                }
            });

            // delegation scheme
            List<Integer> parentScopeIds = new LinkedList<Integer>();
            for(Node<Scope> parentNode : parentScopeNodes)
            {
                parentScopeIds.add(parentNode.getData().getScopeId());
            }

            scope2context.put(scope.getScopeId(), new ParentDelegationContextImpl(scope.getScopeId(), externalContext, parentScopeIds,
                    new Scopes() {
                        @Override
                        public StatementContext get(Integer scopeId) {
                            return scope2context.get(scopeId);
                        }
                    }));
        }

        return scope2context.get(scope.getScopeId());
    }

    public void activateScope(QName targetUnit) {


        int scopeId = getScopeId(targetUnit);
        QName currentlyActive = activeInScope.get(scopeId);

        if(!targetUnit.equals(currentlyActive))
        {
            System.out.println("Replace "+currentlyActive+" with "+ targetUnit);
        }

        activeInScope.put(scopeId, targetUnit);

    }

    /**
     * A unit can be activated if the parent is a demarcation type
     * or it is a non demarcating root element
     *
     * @param interactionUnit
     * @return
     */
    public boolean canBeActivated(QName interactionUnit) {

        Node<Scope> node = dialog.getScopeModel().findNode(interactionUnit);
        assert node!=null : "Unit doesn't exist in shim: "+interactionUnit;
        boolean nonDemarcatingRootElement = node.getParent() == null
                && !node.getData().isDemarcationType();
        boolean parentIsDemarcationType = node.getParent()!=null && node.getParent().getData().isDemarcationType();
        return nonDemarcatingRootElement || parentIsDemarcationType;
    }

    public boolean isWithinActiveScope(QName unitId) {
        int scopeId = getScopeId(unitId);
        QName activeUnit= activeInScope.get(scopeId); // does the scope have an active unit?
        return activeUnit!=null;
    }

   /* private int getParentScopeId(QName targetUnit) {
        final int selfScope = getScopeId(targetUnit);
        Node<Scope> parent = dialog.getScopeModel().findNode(targetUnit).findParent(new NodePredicate<Scope>() {
            @Override
            public boolean appliesTo(Node<Scope> node) {
                return node.getData().getScopeId() != selfScope;
            }
        });

        // fallback to root scope if none found
        return parent!= null ?
                parent.getData().getScopeId() : 0;
    }*/

    private int getScopeId(QName targetUnit) {
        MutableContext context = (MutableContext)getContext(targetUnit);
        assert context!=null : "No context for "+targetUnit;
        return context.getScopeId();
    }

    interface MutableContext extends StatementContext {
        Integer getScopeId();
        String get(String key);
        String[] getTuple(String key);
        void setStatement(String key, String value);
        void clearStatement(String key);
    }

    interface Scopes {
        StatementContext get(Integer scopeId);
    }

}
