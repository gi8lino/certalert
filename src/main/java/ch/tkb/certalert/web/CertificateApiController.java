package ch.tkb.certalert.web;

import ch.tkb.certalert.collector.CertificateCollector;
import ch.tkb.certalert.model.CertificateInfo;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** JSON API for certificate data. */
@RestController
public class CertificateApiController {

  private final CertificateCollector collector;

  public CertificateApiController(CertificateCollector collector) {
    this.collector = collector;
  }

  @GetMapping("/api/certificates")
  public List<CertificateInfo> certificates() {
    return collector.getCertificateInfos();
  }
}
