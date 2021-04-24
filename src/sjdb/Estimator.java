package sjdb;

import java.util.Iterator;
import java.util.List;

public class Estimator implements PlanVisitor {

	private int sumOfCost;		// record the cost estimation


	public Estimator() {
		// empty constructor
	}

	/* 
	 * Create output relation on Scan operator
	 *
	 * Example implementation of visit method for Scan operators.
	 */
	public void visit(Scan op) {
		Relation input = op.getRelation();
		Relation output = new Relation(input.getTupleCount());
		
		Iterator<Attribute> iter = input.getAttributes().iterator();
		while (iter.hasNext()) {
			output.addAttribute(new Attribute(iter.next()));
		}
		
		op.setOutput(output);

		sumOfCost += output.getTupleCount();
	}

	public void visit(Project op) {
		Relation input = op.getInput().getOutput();
		Relation output = new Relation(input.getTupleCount());

		// find which attrs should be projected
		// attrsProjected should be subset of input.getAttributes()
		List<Attribute> attrsProjected = op.getAttributes();
		for (Attribute attrContained : input.getAttributes()) {
			if (attrsProjected.contains(attrContained)) {
				output.addAttribute(new Attribute(attrContained));
			}
		}

		op.setOutput(output);
		sumOfCost += output.getTupleCount();
	}
	
	public void visit(Select op) {
	}
	
	public void visit(Product op) {
	}
	
	public void visit(Join op) {
	}
}
