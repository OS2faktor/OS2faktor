package dk.digitalidentity.common.dao.model;

import java.time.LocalDateTime;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.Table;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;

import com.fasterxml.jackson.annotation.JsonIgnore;

import dk.digitalidentity.common.dao.model.enums.LogAction;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "auditlogs")
@Setter
@Getter
public class AuditLog {

	@Id
	@Column
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;
	
	@CreationTimestamp
	@Column
	private LocalDateTime tts;
		
	@Column
	private String ipAddress;
	
	@Column
	private String correlationId;

	// referenced person which data was used

	@BatchSize(size = 100)
	@NotFound(action = NotFoundAction.IGNORE)
	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "person_id")
	@JsonIgnore
	private Person person;
	
	@Column
	private String personName;

	@Column
	private String personDomain;
	
	@Column
	private String cpr;
	
	// referenced entity that performed the action (null if the performer was the data-owner)

	@Column
	private Long performerId;
	
	@Column
	private String performerName;
	
	// action performed

	@Column
	@Enumerated(EnumType.STRING)
	private LogAction logAction;
	
	@Column
	private String logTargetId;
	
	@Column
	private String logTargetName;
	
	@Column
	private String message;
	
	@OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
	@JoinColumn(name = "auditlogs_details_id")
	@JsonIgnore
	private AuditLogDetail details;
}
