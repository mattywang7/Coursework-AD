package sjdb;

import java.util.*;
import java.util.stream.Collectors;

public class Optimiser2 implements PlanVisitor {

    private Catalogue cat;
    private Set<Attribute> allAttributes = new HashSet<>();
    private Set<Predicate> allPredicates = new HashSet<>();
    private Set<Scan> allScans = new HashSet<Scan>();
    private static final Estimator est = new Estimator(); // the Estimator in Use here

    public Optimiser2(Catalogue cat) {
        this.cat = cat;
    }

    public void visit(Scan op) { allScans.add(new Scan((NamedRelation)op.getRelation())); }
    public void visit(Project op) { allAttributes.addAll(op.getAttributes()); }
    public void visit(Product op) {}
    public void visit(Join op) {}
    public void visit(Select op) {
        allPredicates.add(op.getPredicate());
        allAttributes.add(op.getPredicate().getLeftAttribute());
        if(!op.getPredicate().equalsValue())
            allAttributes.add(op.getPredicate().getRightAttribute());
    }

    public Operator optimise(Operator plan) {
        plan.accept(this);
        //move down the selections and projections
        List<Operator> operation = SelectProjectDown(allScans, allAttributes, allPredicates, plan);
        // reorder predicate order
        Operator optPlan = ReOrder(allPredicates, operation, plan);
        return optPlan;
    }

    /**
     * for each scan or relations at the leaves, process it as much as possible.
     * make down the select and project down
     */
    private static List<Operator> SelectProjectDown(Set<Scan> scans, Set<Attribute> attrs, Set<Predicate> predicates, Operator root) {

        // the block of resultant operators from each of the SCANs
        List<Operator> operator = new ArrayList<>(scans.size());

        for (Scan s: scans){
            Operator oprt = s;
            List<Attribute> availableAttrs = oprt.getOutput().getAttributes();
            Iterator<Predicate> it = predicates.iterator();
            //predicates will be iterated and judge if any on root of operator
            while(it.hasNext()) {
                Predicate currentPred = it.next();
                if(oprt.getOutput() == null)
                    oprt.accept(est);

                if((currentPred.equalsValue() && availableAttrs.contains(currentPred.getLeftAttribute()))) {
                    oprt = new Select(oprt, currentPred);
                    it.remove();
                }
                if(!currentPred.equalsValue() && availableAttrs.contains(currentPred.getLeftAttribute())) {
                    if(availableAttrs.contains(currentPred.getRightAttribute())) {
                        oprt = new Select(oprt, currentPred);
                        it.remove();
                    }
                }
            }
            Operator o = oprt;
            List<Predicate> temp = new ArrayList<>();
            temp.addAll(predicates);
            AttributeNeeds(temp, root);
            //scan the attributes and find attributes are needed
            Operator op3;
            //operator has output
            if(o.getOutput() == null)
                o.accept(est);
            //choose attributes be projected
            List<Attribute> attrsToProjectFromOp = new ArrayList<>(AttributeNeeds(temp, root));
            attrsToProjectFromOp.retainAll(o.getOutput().getAttributes());

            if(attrsToProjectFromOp.size() > 0) {
                Operator oprt2 = new Project(o, attrsToProjectFromOp);
                oprt2.accept(est);
                op3 = oprt2;
            }
            else
                op3 = o;

            operator.add(op3);
        }
        return operator;
    }

    //Make a join ordering for each query, Estimate the cost and Select the cheapest ordering
    private static Operator ReOrder(Set<Predicate> OldPreds, List<Operator> ops, Operator root){

        //list of predicates
        List<Predicate> preds = new ArrayList<>();
        preds.addAll(OldPreds);

        // Permuations of predicates
        List<List<Predicate>> PerPredicates = PerGenerate(preds);

        // find cheapest cost
        Operator CheapestPlan = null;
        Integer CheapestCost = Integer.MAX_VALUE;

        for (List<Predicate> p : PerPredicates) {
            List<Operator> tempOps = new ArrayList<>();
            tempOps.addAll(ops);

            // tree structure
            Operator aPlan = ProductOrJoin(tempOps, p, root);
            Integer i = est.getSumOfCost(aPlan);
            //System.out.println("Found plan with cost: " + i);

            // make the cheapest plan
            if(i < CheapestCost) {
                CheapestPlan = aPlan;
                CheapestCost = i;
            }
        }
        return CheapestPlan;
    }

    /**
     * Scan the predicates and check if any can be applied to Operators.
     *
     * Product --> No Found
     * Select --> 1 Operator
     * Join --> 2 Operators
     *
     */
    private static Operator ProductOrJoin(List<Operator> ops, List<Predicate> preds, Operator root){

        Operator result = null;
        Operator left = null;
        Operator right = null;

        if (ops.size() == 1){
            result = ops.get(0);
            if (result.getOutput() == null) result.accept(est);
            return result;
        }

        // First Iterate over the predicates and until joins or selects applied

        Iterator<Predicate> it = preds.iterator();
        while(it.hasNext()){

            Predicate currentPred = it.next();
            // The potential Operator with the left ATTRIBUTE in its output Relation

            //scan the operators
            Iterator<Operator> oIt = ops.iterator();

            //Checks a list of Operators to see if any has the ATTRIBUTE in its output Relation.
            //If there is attribute in ourput relation , then the Operator will be extracted .

            while(oIt.hasNext()) {
                Operator curOp = oIt.next();
                //find the operator with attributes in output relation
                if(curOp.getOutput().getAttributes().contains(currentPred.getLeftAttribute())) {
                    oIt.remove();
                    left = curOp;
                }
                if(curOp.getOutput().getAttributes().contains(currentPred.getRightAttribute())) {
                    oIt.remove();
                    right = curOp;
                }

            }

            // Select --> 1 Operator
            if((left == null && right != null) || (right == null && left != null)){
                result = new Select(left != null? left : right, currentPred);
                it.remove();
            }

            // Join --> 2 Operators
            if(left != null && right != null){
                result = new Join(left, right, currentPred);
                it.remove();
            }

            if (result.getOutput() == null)
                result.accept(est);

            Set<Attribute> neededAttrs = AttributeNeeds(preds, root);
            List<Attribute> availableAttrs = result.getOutput().getAttributes();

            // No attributes
            if (neededAttrs.size() == availableAttrs.size() && availableAttrs.containsAll(neededAttrs)){
                ops.add(result);
            }
            else{
                List<Attribute> attrsToKeep = availableAttrs.stream().filter(attr -> neededAttrs.contains(attr)).collect(Collectors.toList());

                if (attrsToKeep.size() == 0) {
                    ops.add(result);
                }
                else {
                    Project tempProj = new Project(result, attrsToKeep);
                    tempProj.accept(est);
                    ops.add(tempProj);
                }
            }
        }
        //if there are more than one operators
        while(ops.size() > 1) {
            // Get the first two
            Operator op1 = ops.get(0);
            Operator op2 = ops.get(1);
            Operator product = new Product(op1, op2);
            product.accept(est);

            // remove the first two and add the new one
            ops.remove(op1);
            ops.remove(op2);
            ops.add(product);
        }
        return ops.get(0);
    }




    /**
     * Get the Set of ATTRIBUTEs that are needed based on the List PREDICATEs and the Operator
     *
     */
    private static Set<Attribute> AttributeNeeds(List<Predicate> predicates, Operator root){

        //set of attributes needed
        Set<Attribute> AttrNeeds = new HashSet<>();

        // iterate predicates
        Iterator<Predicate> predIt = predicates.iterator();
        while(predIt.hasNext()){
            Predicate currentPred = predIt.next();
            Attribute aleft = currentPred.getLeftAttribute();
            Attribute aright = currentPred.getRightAttribute();
            AttrNeeds.add(aleft);
            if (aright != null)
                AttrNeeds.add(aright);
        }

        // Add roots attributes
        if (root instanceof Project)
            AttrNeeds.addAll(((Project) root).getAttributes());
        return AttrNeeds;
    }

    /**
     * get all permutations of predicates
     */
    private static List<List<Predicate>> PerGenerate(List<Predicate> attrs) {

        // one element
        if (attrs.size() == 0) {
            List<List<Predicate>> result = new ArrayList<List<Predicate>>();
            result.add(new ArrayList<Predicate>());
            return result;
        }

        // more than one elements
        Predicate first = attrs.remove(0);
        List<List<Predicate>> res = new ArrayList<List<Predicate>>();
        // recursively call,rollback
        List<List<Predicate>> permutations = PerGenerate(attrs);

        // iterate permutations
        for (List<Predicate> t : permutations) {
            for (int i=0; i <= t.size(); i++) {
                List<Predicate> temp = new ArrayList<Predicate>(t);
                temp.add(i, first);
                res.add(temp);
            }
        }

        return res;
    }

}
