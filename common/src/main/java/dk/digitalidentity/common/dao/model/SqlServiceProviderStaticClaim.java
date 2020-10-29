package dk.digitalidentity.common.dao.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "sql_service_provider_static_claims")
@Setter
@Getter
public class SqlServiceProviderStaticClaim {
	
    @Id
    @Column
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    private SqlServiceProviderConfiguration configuration;

    @NotNull
    @Size(max = 255)
    @Column(name = "claim_field")
    private String field;

    @NotNull
    @Size(max = 255)
    @Column(name = "claim_value")
    private String value;
}
