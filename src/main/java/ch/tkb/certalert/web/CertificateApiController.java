package ch.tkb.certalert.web;

import ch.tkb.certalert.collector.CertificateCollector;
import ch.tkb.certalert.model.CertificateInfo;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** JSON API for certificate data. */
@RestController
public class CertificateApiController {

  /** Service that owns the current certificate snapshot. */
  private final CertificateCollector collector;

  /** Creates the API controller backed by the certificate collector. */
  public CertificateApiController(CertificateCollector collector) {
    this.collector = collector;
  }

  /** Returns the current certificate snapshot as JSON. */
  @GetMapping("/api/certificates")
  public List<CertificateInfo> certificates() {
    return collector.getCertificateInfos();
  }
}
