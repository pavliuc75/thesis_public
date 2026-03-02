package generator.businessObjectState.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ClassType {
    public String name;
    public List<State> states;
}
