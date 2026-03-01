package org.camunda.bpm.getstarted.loanapproval.delegates.validation.organization;

import java.util.ArrayList;
import java.util.List;

public class Role {
    private final String id;
    private final String name;
    private final List<Actor> actors = new ArrayList<>();

    public Role(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId()   { return id; }
    public String getName() { return name; }

    public List<Actor> getActors() {
        return actors;
    }

    public void addActor(Actor actor) {
        if (!actors.contains(actor)) {
            actors.add(actor);
        }
    }

    @Override
    public String toString() {
        return "Role{id='" + id + "', name='" + name + "', actors=" + actors + "}";
    }
}

