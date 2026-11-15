package app.giaky.platform.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** The single {@code mail_settings} row. */
@Entity
@Table(name = "mail_settings")
public class MailSettingsJpaEntity {

    /** The one row's fixed key, inserted by V16. */
    static final UUID SINGLETON_ID = UUID.fromString("00000000-0000-0000-0000-00000000a11e");

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "host", length = 255)
    private String host;

    @Column(name = "port")
    private Integer port;

    @Column(name = "username", length = 320)
    private String username;

    @Column(name = "password_ciphertext")
    private String passwordCiphertext;

    @Column(name = "security", nullable = false, length = 10)
    private String security;

    @Column(name = "from_address", length = 320)
    private String fromAddress;

    @Column(name = "from_name", length = 150)
    private String fromName;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    protected MailSettingsJpaEntity() {
        // JPA
    }

    public UUID getId() {
        return id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordCiphertext() {
        return passwordCiphertext;
    }

    public void setPasswordCiphertext(String passwordCiphertext) {
        this.passwordCiphertext = passwordCiphertext;
    }

    public String getSecurity() {
        return security;
    }

    public void setSecurity(String security) {
        this.security = security;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }

    public String getFromName() {
        return fromName;
    }

    public void setFromName(String fromName) {
        this.fromName = fromName;
    }

    public long getVersion() {
        return version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(UUID updatedBy) {
        this.updatedBy = updatedBy;
    }
}
