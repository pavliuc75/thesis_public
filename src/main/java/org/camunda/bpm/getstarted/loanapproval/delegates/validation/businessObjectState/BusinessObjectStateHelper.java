package org.camunda.bpm.getstarted.loanapproval.delegates.validation.businessObjectState;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;

public class BusinessObjectStateHelper {
    public static BusinessObjectState loadDataFromFile(String filePath) throws IOException {
        ObjectMapper mapper = new ObjectMapper();

        return mapper.readValue(new File(filePath), BusinessObjectState.class);
    }
}
