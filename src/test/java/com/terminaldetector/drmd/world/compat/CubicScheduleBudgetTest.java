package com.terminaldetector.drmd.world.compat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CubicScheduleBudgetTest {
    @Test void recursiveWorkSharesTheOriginalDeadline(){
        var budget=new CubicScheduleBudget();budget.begin(100,50);
        assertTrue(budget.admit(120));assertTrue(budget.admit(149));assertFalse(budget.admit(150));assertFalse(budget.admit(170));
    }
    @Test void deferredWorkCanResumeInTheNextPass(){
        var budget=new CubicScheduleBudget();budget.begin(100,0);assertFalse(budget.admit(100));budget.end(101);
        budget.begin(200,50);assertTrue(budget.admit(201));budget.end(202);
    }
    @Test void nanoTimeWrapDoesNotExpireAnUnspentAllowance(){
        var budget=new CubicScheduleBudget();long start=Long.MAX_VALUE-5;budget.begin(start,10);
        assertTrue(budget.admit(start+4));assertTrue(budget.admit(start+9));assertFalse(budget.admit(start+10));budget.end(start+11);
    }
    @Test void finishedPassDoesNotCancelUnrelatedCalls(){
        var budget=new CubicScheduleBudget();assertTrue(budget.admit(10));budget.begin(20,1);assertFalse(budget.admit(22));budget.end(23);assertTrue(budget.admit(24));
    }
    @Test void WorldsDoNotSpendEachOthersAllowances(){
        var first=new CubicScheduleBudget();var second=new CubicScheduleBudget();first.begin(0,1);second.begin(0,100);
        assertFalse(first.admit(5));assertTrue(second.admit(5));
    }
}
