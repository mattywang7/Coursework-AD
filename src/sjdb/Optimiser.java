package sjdb;

import java.util.ArrayList;
import java.util.List;

public class Optimiser implements PlanVisitor {

    private final Catalogue catalogue;
    private final List<Scan> scanList;
    private final List<Predicate> predList;
    private final List<Attribute> attrList;
    private final List<Operator> opList;        // store newly created ops

    public Optimiser(Catalogue catalogue) {
        this.catalogue = catalogue;
        scanList = new ArrayList<>();
        predList = new ArrayList<>();
        attrList = new ArrayList<>();
        opList = new ArrayList<>();
    }

    /**
     * Move SELECT operators down the tree.
     * Reorder subtrees to put most restrictive SELECT first.
     * Combine PRODUCT and SELECT to create JOIN.
     * Move PROJECT operators dow the tree.
     * @param plan
     * @return
     */
    public Operator optimise(Operator plan) {
        plan.accept(this);
        pushSelectAndProjectDown(opList, plan, scanList, predList);
    }

    /**
     * Push SELECT and PROJECT down the tree at the mean time.
     * @param opList
     * @param plan
     * @param scanList
     * @param predList
     * @param attrList
     */
    private void pushSelectAndProjectDown(List<Operator> opList, Operator plan,
                                          List<Scan> scanList, List<Predicate> predList) {
        for (Scan scan : scanList) {
            if (scan.getOutput() == null) {
                scan.accept(new Estimator());  // set the output
            }
            // get the scanned output attrs
            // push the SELECT down the tree
            List<Attribute> outputAttrs = scan.getOutput().getAttributes();
            Operator freshSelect = null;
            for (Predicate pred : predList) {
                if (pred.equalsValue()) {
                    if (outputAttrs.contains(pred.getLeftAttribute())) {
                        freshSelect = new Select(scan, pred);
                        predList.remove(pred);
                    }
                } else {
                    if (outputAttrs.contains(pred.getLeftAttribute()) && outputAttrs.contains(pred.getRightAttribute())) {
                        freshSelect = new Select(scan, pred);
                        predList.remove(pred);
                    }
                }
            }

            // set the output of newSelect
            assert freshSelect != null;
            if (freshSelect.getOutput() == null) {
                freshSelect.accept(new Estimator());
            }

            // get the attrs that should be projected
            List<Attribute> projectedAttrs = new ArrayList<>();
            for (Predicate pred : predList) {
                Attribute leftAttr = pred.getLeftAttribute();
                Attribute rightAttr = pred.getRightAttribute();
                projectedAttrs.add(leftAttr);
                if (rightAttr != null) {
                    projectedAttrs.add(rightAttr);
                }
            }
            if (plan instanceof Project) {
                projectedAttrs.addAll(((Project) plan).getAttributes());
            }
            boolean hasProjAttr = projectedAttrs.retainAll(freshSelect.getOutput().getAttributes());
            if (hasProjAttr && projectedAttrs.size() > 0) {
                Operator freshProject = new Project(freshSelect, projectedAttrs);
                freshProject.accept(new Estimator());
                opList.add(freshProject);
            } else if (projectedAttrs.size() == 0) {
                // no project, only select
                opList.add(freshSelect);
            }
        }
    }

    /**
     * Create new SCAN operators and add to list.
     * @param op Scan operator to be visited
     */
    @Override
    public void visit(Scan op) {
        String relName = op.toString();
        Scan scan = null;
        try {
            scan = new Scan(catalogue.getRelation(relName));
        } catch (DatabaseException e) {
            e.printStackTrace();
        }
        scanList.add(scan);
    }

    /**
     * Add all the projected attrs to list.
     * @param op Project operator to be visited
     */
    @Override
    public void visit(Project op) {
        attrList.addAll(op.getAttributes());
    }

    /**
     * Add predicates and their associated attrs to list.
     * @param op Select operator to be visited
     */
    @Override
    public void visit(Select op) {
        Predicate pred = op.getPredicate();
        Attribute leftAttr = pred.getLeftAttribute();
        Attribute rightAttr = pred.getRightAttribute();
        attrList.add(leftAttr);
        if (rightAttr != null) {
            attrList.add(rightAttr);
        }
        predList.add(pred);
    }

    @Override
    public void visit(Product op) {
        // empty function
    }

    @Override
    public void visit(Join op) {
        // empty function
    }
}
