
package com.company.model;

import java.util.List;
import org.bonitasoft.engine.bdm.dao.BusinessObjectDAO;

public interface ClarificationMessageDAO
    extends BusinessObjectDAO
{


    public ClarificationMessage findByPersistenceId(Long persistenceId);

    public List<ClarificationMessage> findByMessage(String message, int startIndex, int maxResults);

    public List<ClarificationMessage> findByTimestampp(Long timestampp, int startIndex, int maxResults);

    public List<ClarificationMessage> find(int startIndex, int maxResults);

    public Long countForFindByMessage(String message);

    public Long countForFindByTimestampp(Long timestampp);

    public Long countForFind();

    public ClarificationMessage newInstance();

}
