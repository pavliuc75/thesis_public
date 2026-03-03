
package com.company.model;

import java.util.List;
import org.bonitasoft.engine.bdm.dao.BusinessObjectDAO;

public interface LoggedAbsenceRecordDAO
    extends BusinessObjectDAO
{


    public LoggedAbsenceRecord findByPersistenceId(Long persistenceId);

    public List<LoggedAbsenceRecord> findByRecordId(Integer recordId, int startIndex, int maxResults);

    public List<LoggedAbsenceRecord> findByAbsenceRequestId(Integer absenceRequestId, int startIndex, int maxResults);

    public List<LoggedAbsenceRecord> findByLoggedDate(Long loggedDate, int startIndex, int maxResults);

    public List<LoggedAbsenceRecord> find(int startIndex, int maxResults);

    public Long countForFindByRecordId(Integer recordId);

    public Long countForFindByAbsenceRequestId(Integer absenceRequestId);

    public Long countForFindByLoggedDate(Long loggedDate);

    public Long countForFind();

    public LoggedAbsenceRecord newInstance();

}
