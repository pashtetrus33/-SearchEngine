package searchengine.model;

import lombok.Data;

import javax.persistence.*;


@Entity
@Table(name = "page")
@Data
public class WebPage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "site_id", nullable = false, referencedColumnName = "id")
    private WebSite webSite;

    @Column(name = "path", nullable = false, columnDefinition = "TEXT")
    //@Pattern(regexp = "^/.*", message = "Path must start with a slash")
    private String path;

    @Column(name = "code", nullable = false)
    private Integer code;

    @Column(name = "content", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;
}