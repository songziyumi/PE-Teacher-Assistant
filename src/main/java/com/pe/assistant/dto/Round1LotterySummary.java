package com.pe.assistant.dto;

public class Round1LotterySummary {

    private final int firstChoiceConfirmedCount;
    private final int secondChoiceConfirmedCount;
    private final int unsuccessfulCount;
    private final int submittedStudentCount;

    public Round1LotterySummary(int firstChoiceConfirmedCount,
                                int secondChoiceConfirmedCount,
                                int unsuccessfulCount,
                                int submittedStudentCount) {
        this.firstChoiceConfirmedCount = firstChoiceConfirmedCount;
        this.secondChoiceConfirmedCount = secondChoiceConfirmedCount;
        this.unsuccessfulCount = unsuccessfulCount;
        this.submittedStudentCount = submittedStudentCount;
    }

    public int getFirstChoiceConfirmedCount() {
        return firstChoiceConfirmedCount;
    }

    public int getSecondChoiceConfirmedCount() {
        return secondChoiceConfirmedCount;
    }

    public int getUnsuccessfulCount() {
        return unsuccessfulCount;
    }

    public int getSubmittedStudentCount() {
        return submittedStudentCount;
    }
}
