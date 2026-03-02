package generator.businessObjectState.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class State {
    public String name;
    public List<String> requiredFields;
}
