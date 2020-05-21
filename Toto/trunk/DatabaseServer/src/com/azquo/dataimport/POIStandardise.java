package com.azquo.dataimport;


import org.apache.poi.ss.formula.OperationEvaluationContext;
import org.apache.poi.ss.formula.eval.*;
import org.apache.poi.ss.formula.functions.FreeRefFunction;

public class POIStandardise implements FreeRefFunction {
    @Override
    public ValueEval evaluate(ValueEval[] valueEvals, OperationEvaluationContext operationEvaluationContext) {
        if (valueEvals.length != 1) {
            return ErrorEval.VALUE_INVALID;
        }
        try {
            ValueEval valueEval = OperandResolver.getSingleValue( valueEvals[0],
                    operationEvaluationContext.getRowIndex(),
                    operationEvaluationContext.getColumnIndex() ) ;
            String s = OperandResolver.coerceValueToString(valueEval);
            return new StringEval(s.replaceAll("[^0-9a-zA-Z]", "").toLowerCase());
        } catch (EvaluationException e) {
            return e.getErrorEval();
        }
    }
}
