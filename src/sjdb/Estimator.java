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
		for (Attribute attr : attrsProjected) {
			for (Attribute attrInput : input.getAttributes()) {
				if (attr.equals(attrInput)) {
					output.addAttribute(attrInput);
				}
			}
		}

		op.setOutput(output);
		sumOfCost += output.getTupleCount();
	}
	
	public void visit(Select op) {
		Relation input = op.getInput().getOutput();
		Relation output;

		// get the predicate of Select op
		Predicate predicate = op.getPredicate();
		Attribute leftAttr = predicate.getLeftAttribute();

		// two forms
		if (predicate.equalsValue()) {
			// attr=val
			Attribute leftAttrInput = input.getAttribute(leftAttr);
			// when a=const, T(S) = T(R) / V(R, a)
			output = new Relation((int) Math.ceil((double) input.getTupleCount() / leftAttrInput.getValueCount()));

			for (Attribute attrInput : input.getAttributes()) {
				if (attrInput.equals(leftAttrInput)) {
					// select the only value which equals to val
					output.addAttribute(new Attribute(attrInput.getName(), 1));
				} else {
					output.addAttribute(new Attribute(attrInput));
				}
			}

			sumOfCost += output.getTupleCount();
		} else {
			// ATTR=attr
			Attribute leftAttrInput = input.getAttribute(leftAttr);
			Attribute rightAttrInput = input.getAttribute(predicate.getRightAttribute());

			int outputTuples = Math.max(leftAttr.getValueCount(), rightAttrInput.getValueCount());
			// when ATTR=attr, T(S) = T(R) / max(V(R, A), V(R, B))
			output = new Relation((int) Math.ceil((double) input.getTupleCount() / outputTuples));

			// when ATTR=attr, V(R, A) = min(V(R, A), V(R, B))
			// when ATTR=attr, V(R, B) = min(V(R, A), V(R, B))
			int attrValues = Math.min(leftAttrInput.getValueCount(), rightAttrInput.getValueCount());
			for (Attribute attrInput : input.getAttributes()) {
				if (attrInput.equals(leftAttrInput) || attrInput.equals(rightAttrInput)) {
					output.addAttribute(new Attribute(attrInput.getName(), attrValues));
				} else {
					output.addAttribute(new Attribute(attrInput));
				}
			}

			sumOfCost += output.getTupleCount();
		}

		op.setOutput(output);
	}
	
	public void visit(Product op) {
		// get the two operands
		Relation leftInput = op.getLeft().getOutput();
		Relation rightInput = op.getRight().getOutput();
		Relation output = new Relation(leftInput.getTupleCount() * rightInput.getTupleCount());

		// knowing all attributes have unique global names
		// no renaming required
		for (Attribute attrLeftInput : leftInput.getAttributes()) {
			output.addAttribute(new Attribute(attrLeftInput));
		}
		for (Attribute attrRightInput : rightInput.getAttributes()) {
			output.addAttribute(new Attribute(attrRightInput));
		}

		op.setOutput(output);
		sumOfCost += output.getTupleCount();
	}
	
	public void visit(Join op) {
	}
}
