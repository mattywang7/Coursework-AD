package sjdb;

import java.util.ArrayList;
import java.util.List;

public class Optimiser implements PlanVisitor {

    private final Catalogue catalogue;
    private final List<Scan> scanList;
    private final List<Predicate> predList;
    private final List<Attribute> attrList;
    private final List<Operator> opList;        // store newly created ops
    private final Estimator estimator;

    public Optimiser(Catalogue catalogue) {
        this.catalogue = catalogue;
        scanList = new ArrayList<>();
        predList = new ArrayList<>();
        attrList = new ArrayList<>();
        opList = new ArrayList<>();
        estimator = new Estimator();
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
        return reorderSubtrees(opList, plan, predList);
    }

    /**
     * Push SELECT and PROJECT down the tree at the mean time.
     * @param opList
     * @param plan
     * @param scanList
     * @param predList
     */
    private void pushSelectAndProjectDown(List<Operator> opList, Operator plan,
                                          List<Scan> scanList, List<Predicate> predList) {
        for (Scan scan : scanList) {
            if (scan.getOutput() == null) {
                scan.accept(estimator);  // set the output
            }
            // get the scanned output attrs
            // push the SELECT down the tree
            List<Attribute> outputAttrs = scan.getOutput().getAttributes();
            Operator tmpOp = scan;
            for (Predicate pred : predList) {
                if (pred.equalsValue()) {
                    if (outputAttrs.contains(pred.getLeftAttribute())) {
                        tmpOp = new Select(scan, pred);
                        predList.remove(pred);
                    }
                } else {
                    if (outputAttrs.contains(pred.getLeftAttribute()) && outputAttrs.contains(pred.getRightAttribute())) {
                        tmpOp = new Select(scan, pred);
                        predList.remove(pred);
                    }
                }
            }

            Operator opToAdd = tmpOp;
            if (opToAdd.getOutput() == null) {
                opToAdd.accept(estimator);
            }
            List<Predicate> tmpPredList = new ArrayList<>(predList);
            // get the attrs that should be projected
            List<Attribute> projectedAttrs = new ArrayList<>();
            for (Predicate pred : tmpPredList) {
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
            boolean hasProjAttr = projectedAttrs.retainAll(opToAdd.getOutput().getAttributes());
            if (hasProjAttr && projectedAttrs.size() > 0) {
                Operator freshProject = new Project(opToAdd, projectedAttrs);
                freshProject.accept(estimator);
                opList.add(freshProject);
            } else if (projectedAttrs.size() == 0) {
                // no project, only select
                opList.add(opToAdd);
            }
        }
    }

    /**
     * Look through all possible pred combinations,
     * choose the one which cost least.
     * @param opList
     * @param plan
     * @param predList
     * @return the optimised plan which costs least
     */
    private Operator reorderSubtrees(List<Operator> opList, Operator plan, List<Predicate> predList) {
        List<Predicate> tmpPredList = new ArrayList<>(predList);
        List<List<Predicate>> predCombinations = allPossiblePred(tmpPredList);
        Operator planOpt = null;
        int minCost = Integer.MAX_VALUE;

        // find the plan which costs least
        for (List<Predicate> onePred : predCombinations) {
            List<Operator> tmpOpList = new ArrayList<>(opList);
            Operator planTmp = generatePlan(tmpOpList, plan, onePred);
            int cost = estimator.getSumOfCost(planTmp);
            if (cost < minCost) {
                planOpt = planTmp;
                minCost = cost;
            }
        }
        return planOpt;
    }

    private Operator generatePlan(List<Operator> opList, Operator plan, List<Predicate> predList) {
        Operator output = null;
        Operator leftOp = null;
        Operator rightOp = null;

        if (opList.size() == 1) {
            output = opList.get(0);
            if (output.getOutput() == null) {
                output.accept(estimator); // set output relation
            }
            return output;
        }

        // iterate the predList and opList
        for (Predicate pred : predList) {
            Attribute leftAttr = pred.getLeftAttribute();
            Attribute rightAttr = pred.getRightAttribute();

            for (Operator op : opList) {
                Relation opOutput = op.getOutput();
                if (opOutput.getAttributes().contains(leftAttr)) {
                    leftOp = op;
                    opList.remove(op);
                }
                if (opOutput.getAttributes().contains(rightAttr)) {
                    rightOp = op;
                    opList.remove(op);
                }
            }

            // generate SELECT if get a UnaryOperator
            if (leftOp != null && rightOp == null) {
                output = new Select(leftOp, pred);
                predList.remove(pred);
            }
            if (leftOp == null && rightOp != null) {
                output = new Select(rightOp, pred);
                predList.remove(pred);
            }

            // generate JOIN if get a BinaryOperator
            if (leftOp != null && rightOp != null) {
                output = new Join(leftOp, rightOp, pred);
                predList.remove(pred);
            }

            assert output != null;
            // set output
            if (output.getOutput() == null) {
                output.accept(estimator);
            }

            // get attrs that should be projected at last
            List<Attribute> attrParams = new ArrayList<>();
            for (Predicate tmpPred : predList) {
                Attribute tmpLeftAttr = tmpPred.getLeftAttribute();
                Attribute tmpRightAttr = tmpPred.getRightAttribute();
                attrParams.add(tmpLeftAttr);
                if (tmpRightAttr != null) {
                    attrParams.add(tmpRightAttr);
                }
            }
            if (plan instanceof Project) {
                attrParams.addAll(((Project) plan).getAttributes());
            }

            List<Attribute> outputAttrs = output.getOutput().getAttributes();
            if (outputAttrs.containsAll(attrParams) && outputAttrs.size() == attrParams.size()) {
                opList.add(output);
            } else {
                List<Attribute> attrNeedProject = new ArrayList<>();
                for (Attribute outputAttr : outputAttrs) {
                    if (attrParams.contains(outputAttr)) {
                        attrNeedProject.add(outputAttr);
                    }
                }
                // no attr to project
                if (attrNeedProject.isEmpty()) {
                    opList.add(output);
                } else {  // need project
                    Project freshProject = new Project(output, attrNeedProject);
                    freshProject.accept(estimator);
                    opList.add(freshProject);
                }
            }
        }

        // if opList.size() > 1, then perform a product
        while (opList.size() > 1) {
            Operator prodLeft = opList.get(0);
            Operator prodRight = opList.get(1);
            Product freshProduct = new Product(prodLeft, prodRight);
            freshProduct.accept(estimator);
            opList.remove(prodLeft);
            opList.remove(prodRight);
            opList.add(freshProduct);
        }

        // return the optimised plan
        return opList.get(0);
    }

    /**
     *
     * @param predList
     * @return all possible predicate combinations
     */
    private List<List<Predicate>> allPossiblePred(List<Predicate> predList) {
        // recursion boundary
        if (predList.isEmpty()) {
            List<List<Predicate>> addOne = new ArrayList<>();
            addOne.add(new ArrayList<>());
            return addOne;
        }
        // !predList.isEmpty()
        Predicate removeFirst = predList.remove(0);
        List<List<Predicate>> res = new ArrayList<>();
        List<List<Predicate>> combinations = allPossiblePred(predList);
        for (List<Predicate> lackOne : combinations) {
            for (int i = 0; i <= lackOne.size(); i++) {
                List<Predicate> tmp = new ArrayList<>(lackOne);
                tmp.add(i, removeFirst);
                res.add(tmp);
            }
        }
        return res;
    }

    /**
     * Create new SCAN operators and add to list.
     * @param op Scan operator to be visited
     */
    @Override
    public void visit(Scan op) {
        NamedRelation scannedRelation = (NamedRelation) op.getRelation();
        Scan freshScan = new Scan(scannedRelation);
        scanList.add(freshScan);
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
