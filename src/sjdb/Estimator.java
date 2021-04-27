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
			Attribute leftAttrParam = input.getAttribute(leftAttr);
			// when a=const, T(S) = T(R) / V(R, a)
			output = new Relation((int) Math.ceil((double) input.getTupleCount() / leftAttrParam.getValueCount()));

			for (Attribute attrInput : input.getAttributes()) {
				if (attrInput.equals(leftAttrParam)) {
					// select the only value which equals to val
					output.addAttribute(new Attribute(attrInput.getName(), 1));
				} else {
					output.addAttribute(new Attribute(attrInput));
				}
			}

			sumOfCost += output.getTupleCount();
		} else {
			// ATTR=attr
			Attribute leftAttrParam = input.getAttribute(leftAttr);
			Attribute rightAttrParam = input.getAttribute(predicate.getRightAttribute());

			int maxValues = Math.max(leftAttrParam.getValueCount(), rightAttrParam.getValueCount());
			// when ATTR=attr, T(S) = T(R) / max(V(R, A), V(R, B))
			output = new Relation((int) Math.ceil((double) input.getTupleCount() / maxValues));

			// when ATTR=attr, V(R, A) = min(V(R, A), V(R, B))
			// when ATTR=attr, V(R, B) = min(V(R, A), V(R, B))
			int attrValues = Math.min(leftAttrParam.getValueCount(), rightAttrParam.getValueCount());
			for (Attribute attrInput : input.getAttributes()) {
				if (attrInput.equals(leftAttrParam) || attrInput.equals(rightAttrParam)) {
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
		// get the two operands
		Relation leftInput = op.getLeft().getOutput();
		Relation rightInput = op.getRight().getOutput();

		// attr1=attr2
		Predicate predicate = op.getPredicate();
		Attribute leftAttr = predicate.getLeftAttribute();
		Attribute rightAttr = predicate.getRightAttribute();

		// get the two attribute of inputs
		Attribute leftAttrParam = leftInput.getAttribute(leftAttr);
		Attribute rightAttrParam = rightInput.getAttribute(rightAttr);

		// T(R JOIN S) = T(R)T(S) / max(V(R, A), V(R, B))
		int maxValues = Math.max(leftAttrParam.getValueCount(), rightAttrParam.getValueCount());
		int rMultiS = leftInput.getTupleCount() * rightInput.getTupleCount();
		Relation output = new Relation((int) Math.ceil((double) rMultiS / maxValues));

		// V(R, A) = V(R, B) = min(V(R, A), V(R, B))
		int minValues = Math.min(leftAttrParam.getValueCount(), rightAttrParam.getValueCount());

		// iterate two relations respectively
		for (Attribute attrLeftInput : leftInput.getAttributes()) {
			if (attrLeftInput.equals(leftAttrParam)) {
				output.addAttribute(new Attribute(attrLeftInput.getName(), minValues));
			} else {
				output.addAttribute(new Attribute(attrLeftInput));
			}
		}
		for (Attribute attrRightInput : rightInput.getAttributes()) {
			if (attrRightInput.equals(rightAttrParam)) {
				output.addAttribute(new Attribute(attrRightInput.getName(), minValues));
			} else {
				output.addAttribute(new Attribute(attrRightInput));
			}
		}

		op.setOutput(output);
		sumOfCost += output.getTupleCount();
	}

	public int getSumOfCost(Operator plan) {
		this.sumOfCost = 0;
		plan.accept(this);
		return this.sumOfCost;
	}

}
