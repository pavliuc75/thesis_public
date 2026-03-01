package org.camunda.bpm.getstarted.loanapproval.delegates.validation.organization;

public class Actor {
    private final String id;
    private final String name;

    public Actor(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId()   { return id; }
    public String getName() { return name; }

    @Override
    public String toString() {
        return "Actor{id='" + id + "', name='" + name + "'}";
    }
}

