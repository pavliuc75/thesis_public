package generator.businessObjectState;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import generator.businessObjectState.models.ClassType;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BusinessDataStates {
    public List<ClassType> classes;
}
