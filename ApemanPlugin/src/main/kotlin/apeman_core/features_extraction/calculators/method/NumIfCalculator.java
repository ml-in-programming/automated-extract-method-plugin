package apeman_core.features_extraction.calculators.method;

import apeman_core.base_entities.FeatureType;
import apeman_core.pipes.CandidateWithFeatures;
import com.intellij.psi.PsiIfStatement;

import java.util.ArrayList;

public class NumIfCalculator extends NumSimpleElementCalculator {

    public NumIfCalculator(ArrayList<CandidateWithFeatures> candidates) {
        super(candidates, FeatureType.CON_IF);
    }

    class Visitor extends NumSimpleElementCalculator.Visitor {

        @Override
        public void visitIfStatement(PsiIfStatement statement) {
            super.visitIfStatement(statement);
            elementsCounter++;
        }
    }
}
