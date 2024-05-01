package dk.digitalidentity.common.dao;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import dk.digitalidentity.common.dao.model.AuditLog;
import dk.digitalidentity.common.dao.model.enums.LogAction;

public interface AuditLogDao extends JpaRepository<AuditLog, Long> {
	AuditLog getById(Long id);
	List<AuditLog> findByCpr(String cpr);
	List<AuditLog> findByCorrelationId(String id);
	List<AuditLog> findByTtsAfterAndTtsBeforeAndLogAction(LocalDateTime after, LocalDateTime before, LogAction logAction);
	List<AuditLog> findAllByTtsAfter(LocalDateTime after);
	List<AuditLog> findByPersonDomainAndTtsAfter(String personDomain, LocalDateTime after);
	List<AuditLog> findByTtsAfterAndLogActionIn(LocalDateTime tts, LogAction... actions);
	List<AuditLog> findByLogActionIn(LogAction... actions);
	List<AuditLog> findByTtsBetweenAndLogActionIn(Pageable pageable, LocalDateTime tts1, LocalDateTime tts2, LogAction... actions);
	List<AuditLog> findFirst500ByLocationNull();
	long countByTtsAfterAndLogAction(LocalDateTime tts, LogAction logAction);

	/* Need to do this, as there is no cascade-delete to auditlog_details table
	 * 
	 * DELETE dd
	 * FROM auditlogs_details dd
	 * JOIN (
	 *   SELECT d.id
	 *   FROM auditlogs_details d
	 *   LEFT JOIN auditlogs a ON d.id = a.auditlogs_details_id
	 *   WHERE a.id IS NULL
	 *   LIMIT 25000
	 * ) ss ON ss.id = dd.id
	 */
	@Modifying
	@Query(nativeQuery = true, value = "DELETE dd FROM auditlogs_details dd JOIN (SELECT d.id FROM auditlogs_details d LEFT JOIN auditlogs a ON d.id = a.auditlogs_details_id WHERE a.id IS NULL LIMIT 25000) ss ON ss.id = dd.id")
	void deleteUnreferencedAuditlogDetails();

	@Modifying
	@Query(nativeQuery = true, value = "DELETE FROM auditlogs WHERE tts < ?1 AND log_action IN ('LOGIN', 'LOGOUT', 'LOGOUT_IP_CHANGED', 'AUTHN_REQUEST', 'OIDC_JWT_ID_TOKEN', 'LOGOUT_REQUEST', 'LOGOUT_RESPONSE', 'TOO_MANY_ATTEMPTS', 'WRONG_PASSWORD', 'RIGHT_PASSWORD', 'ACCEPT_MFA', 'REJECT_MFA', 'ERROR_SENT_TO_SP', 'SESSION_EXPIRED', 'REJECTED_UNKNOWN_PERSON', 'DEACTIVATE_BY_PWD') LIMIT 25000")
	void deleteLoginsByTtsBefore(LocalDateTime before);

	@Modifying
	@Query(nativeQuery = true, value = "DELETE FROM auditlogs WHERE tts < ?1 LIMIT 25000")
	void deleteByTtsBefore(LocalDateTime before);

	@Modifying
	@Query(nativeQuery = true, value = "DELETE FROM auditlogs WHERE tts < ?1 AND log_action IN ('TRACE_LOG', 'SESSION_KEY_ISSUED', 'SESSION_KEY_EXCHANGED') LIMIT 25000")
	void deleteTraceLogsByTtsBefore(LocalDateTime before);
}
