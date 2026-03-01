package org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObjectState;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;

public class StatesHelper {
    public static BusinessDataStates loadDataFromFile(String filePath) throws IOException {
        ObjectMapper mapper = new ObjectMapper();

        return mapper.readValue(new File(filePath), BusinessDataStates.class);
    }

    public static void validateStatesFile(String businessObjectStatePath) {
        //todo implement
    }
}
