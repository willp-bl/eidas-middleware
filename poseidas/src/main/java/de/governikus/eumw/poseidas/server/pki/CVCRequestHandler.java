/*
 * Copyright (c) 2018 Governikus KG. Licensed under the EUPL, Version 1.2 or as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work except
 * in compliance with the Licence. You may obtain a copy of the Licence at:
 * http://joinup.ec.europa.eu/software/page/eupl Unless required by applicable law or agreed to in writing,
 * software distributed under the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS
 * OF ANY KIND, either express or implied. See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package de.governikus.eumw.poseidas.server.pki;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.DatatypeConverter;
import javax.xml.ws.WebServiceException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.ApplicationContext;

import de.governikus.eumw.eidascommon.Utils;
import de.governikus.eumw.poseidas.cardbase.Hex;
import de.governikus.eumw.poseidas.cardbase.asn1.ASN1;
import de.governikus.eumw.poseidas.cardbase.asn1.npa.CertificateDescription;
import de.governikus.eumw.poseidas.cardbase.asn1.npa.ECCVCPath;
import de.governikus.eumw.poseidas.cardbase.asn1.npa.ECCVCertificate;
import de.governikus.eumw.poseidas.cardbase.asn1.npa.ECPublicKeyPath;
import de.governikus.eumw.poseidas.cardserver.certrequest.CertificateRequest;
import de.governikus.eumw.poseidas.cardserver.certrequest.CertificateRequestGenerator.CertificateRequestResponse;
import de.governikus.eumw.poseidas.cardserver.certrequest.CertificateRequestPath;
import de.governikus.eumw.poseidas.config.schema.PkiServiceType;
import de.governikus.eumw.poseidas.eidmodel.TerminalData;
import de.governikus.eumw.poseidas.gov2server.GovManagementException;
import de.governikus.eumw.poseidas.gov2server.constants.admin.GlobalManagementCodes;
import de.governikus.eumw.poseidas.gov2server.constants.admin.IDManagementCodes;
import de.governikus.eumw.poseidas.gov2server.constants.admin.ManagementMessage;
import de.governikus.eumw.poseidas.server.idprovider.accounting.SNMPDelegate;
import de.governikus.eumw.poseidas.server.idprovider.config.EPAConnectorConfigurationDto;
import de.governikus.eumw.poseidas.server.idprovider.config.SslKeysDto;
import de.governikus.eumw.poseidas.server.pki.PendingCertificateRequest.Status;
import de.governikus.eumw.poseidas.server.pki.caserviceaccess.DvcaCertDescriptionWrapper;
import de.governikus.eumw.poseidas.server.pki.caserviceaccess.PKIServiceConnector;
import de.governikus.eumw.poseidas.server.pki.caserviceaccess.ServiceWrapperFactory;
import de.governikus.eumw.poseidas.server.pki.caserviceaccess.TermAuthServiceWrapper;


/**
 * Implementation of the CVC request process.
 *
 * @author tautenhahn
 */
public class CVCRequestHandler extends BerCaRequestHandlerBase
{

  private static final Log LOG = LogFactory.getLog(CVCRequestHandler.class);

  private static final Map<String, TerminalData> KNOWN_ROOT_CERTS = new HashMap<>();

  private static final String DETEST_EID00001 = "fyGCAbZ/ToIBbl8pAQBCDkRFVEVTVGVJRDAwMDAxf0mCAR0GCgQAfwAHAgICAgOBIKn7V9uh7qm8PmYKkJ2DjXJuO/Yj1SYgKCATSB0fblN3giB9Wgl1/CwwV+72dTBBev/n+4BVwSbcXGzpSktE8zC12YMgJtxcbOlKS0TzMLXZu9d8v5WEFilc9+HOa8zcGP+MB7aEQQSL0q65y35XyyxLSC/8gbevud4n4eO9I8I6RFO9ms4yYlR++DXD2sT9l/hGGhRhHcnCd0UTLe2OVFwdVMcvBGmXhSCp+1fboe6pvD5mCpCdg41xjDl6o7VhpveQHg6Cl0hWp4ZBBBhLtRn8Ko9S3A3HMRL6z+kU8qSbZ43VeZorHf6V4aZjWQFOIvqNZkOEE866bPDiFVdrZzN2v2F69N/pdh0ikBSHAQFfIA5ERVRFU1RlSUQwMDAwMX9MEgYJBAB/AAcDAQICUwX+DwH//18lBgEAAAgBA18kBgEDAAgBA183QJ8l6/r0uR5MYKFoN1TF3AdqMXl1Pvl9n4ywH+Hc07jIPnomYCqx80S+VwYAbXmp/2qXFkBNyDufMOEhOzkxKKI=";

  private static final String DETEST_EID00002 = "fyGCAbZ/ToIBbl8pAQBCDkRFVEVTVGVJRDAwMDAxf0mCAR0GCgQAfwAHAgICAgOBIKn7V9uh7qm8PmYKkJ2DjXJuO/Yj1SYgKCATSB0fblN3giB9Wgl1/CwwV+72dTBBev/n+4BVwSbcXGzpSktE8zC12YMgJtxcbOlKS0TzMLXZu9d8v5WEFilc9+HOa8zcGP+MB7aEQQSL0q65y35XyyxLSC/8gbevud4n4eO9I8I6RFO9ms4yYlR++DXD2sT9l/hGGhRhHcnCd0UTLe2OVFwdVMcvBGmXhSCp+1fboe6pvD5mCpCdg41xjDl6o7VhpveQHg6Cl0hWp4ZBBAlutYv9hiUiOOwmUhhcQ8OlbDIGgaIeN6jmndw4fAxfVROFbv4v3GVuYEiTIS4pRJs2XjBGBaxUE+db4x5kHxKHAQFfIA5ERVRFU1RlSUQwMDAwMn9MEgYJBAB/AAcDAQICUwX+DwH//18lBgEAAAkCAV8kBgEDAAkCAV83QBQRIKD9/AEaUvP3Kzh6PcesqItIaNWul0F4C2/4oLSeX1UWmi0pjvXPlZNdygw98+nULcRfdPIGYxcVSWHmx0Y=";

  private static final String DETEST_EID00004 = "fyGCAbZ/ToIBbl8pAQBCDkRFVEVTVGVJRDAwMDAyf0mCAR0GCgQAfwAHAgICAgOBIKn7V9uh7qm8PmYKkJ2DjXJuO/Yj1SYgKCATSB0fblN3giB9Wgl1/CwwV+72dTBBev/n+4BVwSbcXGzpSktE8zC12YMgJtxcbOlKS0TzMLXZu9d8v5WEFilc9+HOa8zcGP+MB7aEQQSL0q65y35XyyxLSC/8gbevud4n4eO9I8I6RFO9ms4yYlR++DXD2sT9l/hGGhRhHcnCd0UTLe2OVFwdVMcvBGmXhSCp+1fboe6pvD5mCpCdg41xjDl6o7VhpveQHg6Cl0hWp4ZBBHT/Y6uDjHPDA6wAPf7pXPi/Vfkej+vLc5XZQgNuR88YRex4bslbtFOqwoitAjtgZ5E8+bY/kI9JME5c/IswUN2HAQFfIA5ERVRFU1RlSUQwMDAwNH9MEgYJBAB/AAcDAQICUwX8DxP//18lBgECAAUBAV8kBgEFAAUBAV83QFwDWgYRtsWPC1Jh/dAJ3sq33Hp5SC1SSMyhGQWbfYKyFXzwxKSZvPRB79014pSljArxmjSgdiFZUzKFrPFwpQU=";

  private static final String DECVCA_EID00103 = "fyGCAbZ/ToIBbl8pAQBCDkRFQ1ZDQWVJRDAwMTAyf0mCAR0GCgQAfwAHAgICAgOBIKn7V9uh7qm8PmYKkJ2DjXJuO/Yj1SYgKCATSB0fblN3giB9Wgl1/CwwV+72dTBBev/n+4BVwSbcXGzpSktE8zC12YMgJtxcbOlKS0TzMLXZu9d8v5WEFilc9+HOa8zcGP+MB7aEQQSL0q65y35XyyxLSC/8gbevud4n4eO9I8I6RFO9ms4yYlR++DXD2sT9l/hGGhRhHcnCd0UTLe2OVFwdVMcvBGmXhSCp+1fboe6pvD5mCpCdg41xjDl6o7VhpveQHg6Cl0hWp4ZBBIklQZ/H8ZSSLPxrjdJa5qGcG1khbmzwYnDl11z9ZCBfVc+Ge7/v7v1uaA4f0ZfxiraESEkBNiVo78mttcYBjXKHAQFfIA5ERUNWQ0FlSUQwMDEwM39MEgYJBAB/AAcDAQICUwX8DxP//18lBgECAQIAA18kBgEFAQIAA183QE1vCKhqTxhAn2aFOH3Txqf/XWjqT3cUqGG7s7tyHQXTAUrfF2PJKS9xXY6U7ps+G3OrE4JBTr8537Ow+2wJ2+s=";

  private static final String DECVCA_EID00102 = "fyGCAbZ/ToIBbl8pAQBCDkRFQ1ZDQWVJRDAwMTAyf0mCAR0GCgQAfwAHAgICAgOBIKn7V9uh7qm8PmYKkJ2DjXJuO/Yj1SYgKCATSB0fblN3giB9Wgl1/CwwV+72dTBBev/n+4BVwSbcXGzpSktE8zC12YMgJtxcbOlKS0TzMLXZu9d8v5WEFilc9+HOa8zcGP+MB7aEQQSL0q65y35XyyxLSC/8gbevud4n4eO9I8I6RFO9ms4yYlR++DXD2sT9l/hGGhRhHcnCd0UTLe2OVFwdVMcvBGmXhSCp+1fboe6pvD5mCpCdg41xjDl6o7VhpveQHg6Cl0hWp4ZBBDNH7Plv+0vZuFVO+8z8fQskLxBx4ptMnGIseeM52ECvZ765uRJpImXZwWxiVz9Fef/U3i3pK6tAndXF1IJEqfeHAQFfIA5ERUNWQ0FlSUQwMDEwMn9MEgYJBAB/AAcDAQICUwX+DwH//18lBgEAAQABCF8kBgEDAQABCF83QFBnFFxoyulSD1uzSBfxypxDWT21ZAbGo7AGy/PzFOc0ms8Mxr/ry979ELTc8PIx2laXfYj5+QGC0ZkHalZQZFE=";
  static
  {
    // ref PKI
    addKnowRootCert(DETEST_EID00001);
    addKnowRootCert(DETEST_EID00002);
    addKnowRootCert(DETEST_EID00004);
    // prod PKI
    addKnowRootCert(DECVCA_EID00102);
    addKnowRootCert(DECVCA_EID00103);
  }

  private byte[] collectedRequestData;

  private final BerCaPolicy policy;

  private final ApplicationContext applicationContext;

  private static void addKnowRootCert(String base64)
  {
    try
    {
      TerminalData cvc = new TerminalData(DatatypeConverter.parseBase64Binary(base64));
      KNOWN_ROOT_CERTS.put(cvc.getHolderReferenceString(), cvc);
    }
    catch (IOException e)
    {
      throw new IllegalArgumentException("unable to parse given cvc", e);
    }
  }

  /**
   * Create new Object which can be used for one service provider only as long as configuration is not
   * changed.
   * 
   * @param epaConfig The connection configuration for the terminal
   * @param facade The terminal configuration
   */
  CVCRequestHandler(EPAConnectorConfigurationDto epaConfig, TerminalPermissionAO facade)
    throws GovManagementException
  {
    this(epaConfig, facade, facade.getApplicationContext());
  }

  /**
   * Create new Object which can be used for one service provider only as long as configuration is not
   * changed.
   *
   * @param epaConfig The connection configuration for the terminal
   * @param facade The terminal configuration
   * @param applicationContext The spring application context
   */
  CVCRequestHandler(EPAConnectorConfigurationDto epaConfig,
                           TerminalPermissionAO facade,
                           ApplicationContext applicationContext)
    throws GovManagementException
  {
    super(epaConfig, facade);
    policy = PolicyImplementationFactory.getInstance().getPolicy(pkiConfig.getBerCaPolicyId());
    this.applicationContext = applicationContext;
  }

  /**
   * Create and send an initial certificate request. This method assumes that a CVC description has already
   * been created and sent to Deutsche Post before.
   *
   * @param cvcDescription
   * @param countryCode
   * @param chrMnemonic
   * @param sequenceNumber
   */
  void makeInitialRequest(byte[] cvcDescription,
                                 String countryCode,
                                 String chrMnemonic,
                                 int sequenceNumber)
    throws GovManagementException
  {
    try
    {
      byte[][] encodedCvcs = getCACertificates(null);
      TerminalPermission tp = facade.getTerminalPermission(entityId);
      if (tp == null)
      {
        facade.create(entityId);
      }
      PoseidasCertificateRequestGenerator generator = new PoseidasCertificateRequestGenerator(entityId, policy,
                                                                                          facade);
      generator.setDataForFirstRequest(selectRootCert(encodedCvcs),
                                       cvcDescription,
                                       countryCode,
                                       chrMnemonic,
                                       sequenceNumber);
      CertificateRequestResponse response = generator.create();
      String messageId = "#" + Utils.generateUniqueID();
      storeRequestData(entityId,
                       messageId,
                       response.getCertificateRequest(),
                       response.getCertificateDescription(),
                       encodedCvcs,
                       response.getPKCS8PrivateKey());
      LOG.info(entityId + ": Created and stored certificate request \n" + response.getCertificateRequest());
      if (policy.isInitialRequestAsynchron())
      {
        sendCertificateRequest(messageId,
                               pkiConfig.getAutentURL()
                                          + "/eID-Server/TermAuth/Terminal/IS_termcontr/EACPKITermContr",
                               response.getCertificateRequest().getEncoded());
        facade.storeCVCRequestSent(entityId);
      }
      else
      {
        byte[] cert = sendCertificateRequest(null, null, response.getCertificateRequest().getEncoded());
        installNewCertificate(cert);
      }
    }
    catch (IOException | SignatureException e)
    {
      internalError("cannot create cert request", e);
    }
  }

  private void internalError(String msg, Exception e) throws GovManagementException
  {
    LOG.error(entityId + ": " + msg, e);
    throw new GovManagementException(GlobalManagementCodes.INTERNAL_ERROR);
  }

  /**
   * Return a zip file content with communication certificates, input parameters and certificate description
   * collected in the last call of
   * {@link #prepareInitialRequest(String, String, String, boolean, boolean, String, String, String, int, List)}
   */
  public byte[] getCollectedRequestData()
  {
    return collectedRequestData;
  }

  /**
   * create and send a subsequent request, store the obtained data in the persistence layer.
   *
   * @param newCvcDescription optional, use this new CVC description if not null
   * @return null if successful, technical error message otherwise
   */
  ManagementMessage makeSubsequentRequest(TerminalPermission tp,
                                                 byte[] newCvcDescription,
                                                 boolean forceAsyncron)
  {
    return this.makeSubsequentRequest(tp, newCvcDescription, forceAsyncron, false);
  }

  /**
   * create and send a subsequent request, store the obtained data in the persistence layer.
   *
   * @param newCvcDescription optional, use this new CVC description if not null
   * @return null if successful, technical error message otherwise
   */
  ManagementMessage makeSubsequentRequest(TerminalPermission tp,
                                                 byte[] newCvcDescription,
                                                 boolean forceAsyncron,
                                                 boolean forceSendAgain)
  {
    LOG.debug(entityId + ": started makeSubsequentRequest");
    try
    {
      byte[] cvcRequestData = null;
      PendingCertificateRequest pendingRequest = tp.getPendingCertificateRequest();
      String messageId = null;
      if (pendingRequest != null)
      {
        Status requestStatus = pendingRequest.getStatus();
        // The certificate request was sent or is currently send.
        if ((Status.Sent.equals(requestStatus)
             || (Status.Created.equals(requestStatus)
                 && pendingRequest.getLastChanged()
                                  .after(new Date(System.currentTimeMillis() - 10L * 60 * 1000))))
            && !forceSendAgain)
        {
          String message = "cannot request a new certificate as long as there is a pending request for same data with status "
                           + requestStatus + " for " + entityId;
          LOG.error(entityId + ": " + message);
          SNMPDelegate.getInstance().sendSNMPTrap(SNMPDelegate.OID.CERT_RENEWAL_FAILED,
                                                  SNMPDelegate.CERT_RENEWAL_FAILED + " " + message);

          return GlobalManagementCodes.EC_UNEXPECTED_ERROR.createMessage("cannot request a new certificate as long as there is a pending request for same data for "
                                                                         + entityId);
        }
        if (Status.Created.equals(requestStatus) || Status.Sent.equals(requestStatus))
        {
          messageId = pendingRequest.getMessageID();
          cvcRequestData = pendingRequest.getRequestData();
          LOG.warn(entityId + ": Will send existing subsequent certificate request (again), messageID="
                   + messageId + "\n");
        }
        else
        {
          String message = "cannot re-send certificate request as there is already a result with status "
                           + requestStatus + " for " + entityId;
          LOG.error(entityId + ": " + message);
          return GlobalManagementCodes.EC_UNEXPECTED_ERROR.createMessage("cannot re-send certificate request as there is already a result for "
                                                                         + entityId);
        }
      }
      if (cvcRequestData == null)
      {
        byte[][] chain = null;
        try
        {
          chain = getCACertificates(null);
        }
        catch (GovManagementException e)
        {
          LOG.error(entityId + ": cannot download ca certificates, will keep old certificate chain");
          SNMPDelegate.getInstance()
                      .sendSNMPTrap(SNMPDelegate.OID.CERT_RENEWAL_FAILED,
                                    SNMPDelegate.CERT_RENEWAL_FAILED + " " + "cannot download ca certificates for generting a new CVC for "
                                                                          + entityId
                                                                          + ", will use old cert chain");
        }

        if (chain == null) // download failed
        {
          chain = new byte[tp.getChain().size()][];
          for ( CertInChain cin : tp.getChain() )
          {
            chain[cin.getKey().getPosInChain()] = cin.getData();
          }
        }

        CertificateRequestResponse response = createRequest(tp, chain, newCvcDescription);
        messageId = (forceAsyncron ? "as" : "s") + Utils.generateUniqueID();
        storeRequestData(entityId,
                         messageId,
                         response.getCertificateRequest(),
                         response.getCertificateDescription(),
                         null,
                         response.getPKCS8PrivateKey());
        LOG.info(entityId + ": Created and stored subsequent certificate request:\n"
                 + response.getCertificateRequest());
        cvcRequestData = response.getCertificateRequest().getEncoded();
      }

      String chr = new ECCVCertificate(tp.getCvc()).getHolderReferenceString();
      this.facade.archiveCVC(chr, tp.getCvc());
      LOG.debug(entityId + ": CVC " + chr + " successfully archived");

      if (forceAsyncron)
      {
        sendCertificateRequest(messageId,
                               pkiConfig.getAutentURL()
                                          + "/eID-Server/TermAuth/Terminal/IS_termcontr/EACPKITermContr",
                               cvcRequestData);
        LOG.debug(entityId + ": made an asynchronous subsequent request because cvc has expired");
      }
      else
      {
        byte[] newCert = sendCertificateRequest(null, null, cvcRequestData);
        installNewCertificate(newCert);
        LOG.debug(entityId + ": successfully finished makeSubsequentRequest");
      }
      return null;
    }
    catch (GovManagementException e)
    {
      LOG.error(entityId + ": cannot renew certificate", e);
      SNMPDelegate.getInstance().sendSNMPTrap(SNMPDelegate.OID.CERT_RENEWAL_FAILED,
                                              SNMPDelegate.CERT_RENEWAL_FAILED + " " + e.getMessage());
      return e.getManagementMessage();
    }
    catch (Throwable t)
    {
      LOG.error(entityId + ": cannot renew certificate", t);
      SNMPDelegate.getInstance().sendSNMPTrap(SNMPDelegate.OID.CERT_RENEWAL_FAILED,
                                              SNMPDelegate.CERT_RENEWAL_FAILED + " " + t.getMessage());
      return GlobalManagementCodes.EC_UNEXPECTED_ERROR.createMessage(t.getMessage());
    }
  }

  private CertificateRequestResponse createRequest(TerminalPermission tp,
                                                   byte[][] chain,
                                                   byte[] newCvcDescription)
    throws IOException, SignatureException
  {
    PoseidasCertificateRequestGenerator generator = new PoseidasCertificateRequestGenerator(entityId, policy,
                                                                                        facade);
    generator.prepareSubsequentRequest(selectRootCert(chain),
                                       tp.getCvc(),
                                       tp.getCvcPrivateKey(),
                                       (newCvcDescription == null) ? tp.getCvcDescription()
                                         : newCvcDescription);
    return generator.create();
  }

  /**
   * install an obtained CVCert.
   *
   * @param cert
   */
  void installNewCertificate(byte[] cert) throws GovManagementException
  {
    // Disable timer because of possible interference with the initial black list update
    PermissionDataHandling handler = (PermissionDataHandling)applicationContext.getBean("PermissionDataHandling");
    handler.unregisterTimerHandling();

    byte[][] chain = null;
    byte[] certDescription = null;
    try
    {
      ECCVCertificate cvc = new ECCVCertificate(cert);
      String preferedCHR = cvc.getAuthorityReferenceString();
      chain = getCACertificates(preferedCHR);
    }
    catch (IOException e)
    {
      throw new IllegalArgumentException("unable to parse given cvc", e);
    }
    catch (GovManagementException e)
    {
      LOG.error(entityId + ": cannot download ca certificates, will keep old certificate chain");
      SNMPDelegate.getInstance()
                  .sendSNMPTrap(SNMPDelegate.OID.CERT_RENEWAL_FAILED,
                                SNMPDelegate.CERT_RENEWAL_FAILED + " " + "cannot download ca certificates, storing new CVC for  "
                                                                      + entityId + " anyway");
    }

    if (policy.isCertDescriptionFetch())
    {
      certDescription = getCertDescriptionIfNeeded(cert);
    }

    TerminalPermission tp = facade.getTerminalPermission(entityId);

    try
    {
      PendingCertificateRequest pendingCertificateRequest = tp.getPendingCertificateRequest();

      byte[][] chainForCheck = chain;
      if (chainForCheck == null) // download failed
      {
        chainForCheck = new byte[tp.getChain().size()][];
        for ( CertInChain cin : tp.getChain() )
        {
          chainForCheck[cin.getKey().getPosInChain()] = cin.getData();
        }
      }
      byte[] requestData = pendingCertificateRequest.getRequestData();
      CertificateRequest certificateRequest = new CertificateRequest(new ByteArrayInputStream(requestData),
                                                                     true);
      ASN1 publicKey = certificateRequest.getChildElementByPath(CertificateRequestPath.PUBLIC_KEY);
      if (publicKey == null)
      {
        ASN1[] bodies = certificateRequest.getChildElementsByTag(CertificateRequestPath.CV_CERTIFICATE_BODY.getTag());
        ASN1 body = bodies[0];
        ASN1[] publicKeys = body.getChildElementsByTag(CertificateRequestPath.PUBLIC_KEY.getTag());
        publicKey = publicKeys[0];
      }
      ASN1 holderReference = certificateRequest.getChildElementByPath(CertificateRequestPath.HOLDER_REFERENCE);
      checkCVC(cert, tp.getCvc(), chainForCheck, publicKey, holderReference, new Date(), entityId);
    }
    catch (Throwable e)
    {
      LOG.error(entityId + ": check of new CVC failed. Reason: " + e.getMessage() + ". CVC Data:\n"
                + Utils.breakAfter76Chars(DatatypeConverter.printBase64Binary(cert)));
      facade.storeCVCObtainedError(entityId, e.getMessage());
      // restart timer
      handler.registerTimerHandling();
      throw new GovManagementException(GlobalManagementCodes.EC_INVALIDCERTIFICATEDATA);
    }

    facade.storeCVCObtained(entityId, cert, chain, certDescription);
    LOG.debug("Stored new CVC for " + entityId + ":\n" + Hex.dump(cert));
    RestrictedIdHandler riHandler;
    try
    {
      riHandler = new RestrictedIdHandler(nPaConf, facade);
    }
    catch (GovManagementException e)
    {
      // restart timer
      handler.registerTimerHandling();
      throw e;
    }
    if (tp.getSectorID() == null)
    {
      try
      {
        riHandler.requestBlackList(false, false);
      }
      catch (GovManagementException e)
      {
        LOG.error(entityId + ": cannot fetch blacklist", e);
        SNMPDelegate.getInstance()
                    .sendSNMPTrap(SNMPDelegate.OID.BLACKLIST_RENEWAL_FAILED,
                                  SNMPDelegate.BLACKLIST_RENEWAL_FAILED + " " + "cannot fetch blacklist for "
                                                                             + entityId);
      }
    }
    try
    {
      riHandler.requestPublicSectorKeyIfNeeded();
    }
    catch (GovManagementException e)
    {
      LOG.error(entityId + ": cannot fetch public sector key", e);
      SNMPDelegate.getInstance()
                  .sendSNMPTrap(SNMPDelegate.OID.PUBLIC_SECTOR_KEY_REQUEST_FAILED,
                                SNMPDelegate.PUBLIC_SECTOR_KEY_REQUEST_FAILED + " " + "cannot fetch public sector key for "
                                                                                   + entityId);
    }

    try
    {
      MasterAndDefectListHandler mslHandler = new MasterAndDefectListHandler(nPaConf, facade);
      mslHandler.updateLists();
    }
    catch (GovManagementException e)
    {
      // restart timer
      handler.registerTimerHandling();
      throw e;
    }

    // restart timer
    handler.registerTimerHandling();
  }

  private static void checkCVC(byte[] newCvcBytes,
                       byte[] oldCvcBytes,
                       byte[][] chain,
                       ASN1 publicKey,
                       ASN1 holderReference,
                       Date actualDate,
                       String cvcRefId)
    throws CertificateException, GovManagementException
  {
    try
    {
      TerminalData newCVC = new TerminalData(newCvcBytes);

      if ((oldCvcBytes != null) && (oldCvcBytes.length > 1))
      {
        TerminalData oldCVC = new TerminalData(oldCvcBytes);

        // check if the new and old cvc are identical
        if (Arrays.equals(newCvcBytes, oldCvcBytes))
        {
          String detail = String.format("new cvc '%s' is identical with existing cvc '%s'", newCVC, oldCVC);
          throw new CertificateException(detail);
        }

        // check the holder reference
        if (holderReference == null)
        {
          // only if there is no holder reference from request data, check holder reference from old CVC
          byte[] newBasicHolderReference = getBasicHolderReference(newCVC);
          byte[] oldBasicHolderReference = getBasicHolderReference(oldCVC);
          if (!Arrays.equals(oldBasicHolderReference, newBasicHolderReference))
          {
            String detail = String.format("holder reference of new cvc '%s' does not match holder reference of existing cvc '%s'",
                                          newCVC,
                                          oldCVC);
            throw new CertificateException(detail);
          }
        }
        else
        {
          byte[] newBasicHolderReference = getBasicHolderReference(newCVC);
          byte[] requestedBasicHolderReference = getBasicHolderReference(holderReference);
          if (!Arrays.equals(requestedBasicHolderReference, newBasicHolderReference))
          {
            String detail = String.format("holder reference of new cvc '%s' does not match holder reference of certificate request",
                                          newCVC);
            throw new CertificateException(detail);
          }
        }
      }

      try
      {
        // check if chain is present
        if (chain == null)
        {
          LOG.warn(cvcRefId + ": no chain present to check new cvc: "
                   + new String(newCVC.getCAReference(), StandardCharsets.UTF_8));
          String detail = String.format("no chain present to check new cvc '%s'", newCVC);
          throw new CertificateException(detail);
        }

        // check, if the CA reference of the new cvc is present in the chain
        boolean foundInChain = false;
        for ( byte[] cacert : chain )
        {
          TerminalData parsedCaCVC = new TerminalData(cacert);
          if (Arrays.equals(parsedCaCVC.getHolderReference(), newCVC.getCAReference()))
          {
            foundInChain = true;
            break;
          }
        }
        if (!foundInChain)
        {
          String detail = "ca reference of new cvc not found in chain: " + newCVC;
          throw new CertificateException(detail);
        }

        // check the dates
        if (newCVC.getEffectiveDate().after(actualDate))
        {
          String detail = String.format("effective date of new cvc '%s' is after actual date", newCVC);
          throw new CertificateException(detail);
        }
        if (newCVC.getExpirationDate().before(actualDate))
        {
          String detail = String.format("expiration date of new cvc '%s' is before actual date", newCVC);
          throw new CertificateException(detail);
        }

        // check the public key. it must be equal to the public key of the request
        ASN1 newCVCPublicKey = newCVC.getPublicKey();
        ASN1 newCVCPublicPoint = newCVCPublicKey.getChildElementByPath(ECPublicKeyPath.PUBLIC_POINT_Y);
        ASN1 requestPublicPoint = publicKey.getChildElementByPath(ECPublicKeyPath.PUBLIC_POINT_Y);
        if (!requestPublicPoint.equals(newCVCPublicPoint))
        {
          String detail = String.format("public key of new cvc '%s' does not match public key of pending request",
                                        newCVC);
          throw new CertificateException(detail);
        }

        // check the signatures
        List<TerminalData> formattedCVCList = formatCVCList(newCVC, Arrays.asList(chain), cvcRefId);
        try
        {
          if (!newCVC.verify(formattedCVCList))
          {
            String detail = String.format("mathematical signature check of new cvc '%s' failed", newCVC);
            throw new CertificateException(detail);
          }
        }
        catch (IOException e)
        {
          String detail = String.format("mathematical signature check of new cvc '%s' failed", newCVC);
          throw new CertificateException(detail, e);
        }
      }
      catch (IOException e)
      {
        throw new GovManagementException(GlobalManagementCodes.EC_UNEXPECTED_ERROR,
                                         "IOException: " + e.getMessage());
      }
    }
    catch (IOException e)
    {
      throw new IllegalArgumentException("unable to parse given cvc", e);
    }
  }

  /**
   * @param newCVC
   * @return
   */
  private static byte[] getBasicHolderReference(TerminalData newCVC)
  {
    byte[] newHolderReference = new byte[newCVC.getHolderReference().length - 5];
    System.arraycopy(newCVC.getHolderReference(), 0, newHolderReference, 0, newHolderReference.length);
    return newHolderReference;
  }

  /**
   * @param holderReference
   * @return
   */
  private static byte[] getBasicHolderReference(ASN1 holderReference)
  {
    byte[] hrBytes = holderReference.getValue();
    byte[] newHolderReference = new byte[hrBytes.length - 5];
    System.arraycopy(hrBytes, 0, newHolderReference, 0, newHolderReference.length);
    return newHolderReference;
  }

  /**
   * Creates a sorted list from the given list
   *
   * @param terminalCertificate the terminal certificate to build the chain for
   * @param cvcChain the unsorted chain certificates
   * @param cvcRefId
   * @throws IllegalArgumentException if something illegal with the input
   * @return List with the chain certificates without the terminal and the root certificate
   */
  private static List<TerminalData> formatCVCList(TerminalData terminalCertificate,
                                                  List<byte[]> cvcChain,
                                                  String cvcRefId)
  {
    LOG.debug(cvcRefId + ": Parse session input CVC list with size: " + cvcChain.size());
    List<TerminalData> formattedChain = new ArrayList<>();

    // Check the terminal CVC and add it to the list as first element
    if (terminalCertificate.isSelfSigned())
    {
      // FIXME: throw appropriate Exception
      throw new IllegalArgumentException("First CVC in list is self signed and not the terminal CVC");
    }
    TerminalData check = terminalCertificate;
    while (true)
    {
      LOG.debug(cvcRefId + ": Check CVC: " + check.getHolderReferenceString() + "/"
                + new String(check.getCAReference(), StandardCharsets.UTF_8));
      TerminalData holderCVC = getReference(check, cvcChain);
      if (holderCVC != null)
      {
        if (holderCVC.isSelfSigned())
        {
          formattedChain.add(holderCVC);
          break;
        }
        formattedChain.add(holderCVC);
        check = holderCVC;
      }
      else
      {
        break;
      }
    }
    LOG.debug(cvcRefId + ": Parsed session input and set new list with size: " + formattedChain.size());
    return formattedChain;
  }

  private static TerminalData getReference(TerminalData toFind, List<byte[]> cvcChain)
  {
    for ( byte[] cvcBytes : cvcChain )
    {
      try
      {
        TerminalData cvc = new TerminalData(cvcBytes);
        if (Arrays.equals(cvc.getHolderReference(), toFind.getCAReference()))
        {
          return cvc;
        }
      }
      catch (IOException e)
      {
        throw new IllegalArgumentException("unable to parse given cvc", e);
      }
    }
    return null;
  }

  /**
   * Returns a new certificate Description if a new one if needed, because the old one does not match to one
   * in the database any more.
   */
  private byte[] getCertDescriptionIfNeeded(byte[] cert)
  {
    TerminalPermission tp = facade.getTerminalPermission(entityId);
    try
    {
      if (tp.getCvcDescription() == null)
      {
        return fetchCertDescription(cert);
      }
      ECCVCertificate cvcWrapper = new ECCVCertificate(cert);
      CertificateDescription cvcDescription = new CertificateDescription(tp.getCvcDescription());
      if (!cvcWrapper.checkCVCDescriptionHash(cvcDescription))
      {
        return fetchCertDescription(cert);
      }
    }
    catch (Throwable e)
    {
      LOG.error(entityId
                + ": A problem occurred while parsing cvc or cvc description. This could cause problems later",
                e);
    }
    return null;
  }

  private byte[] selectRootCert(byte[][] chain)
  {
    for ( byte[] c : chain )
    {
      if (canBeGivenToRequestGenerator(c))
      {
        return c;
      }
    }
    throw new IllegalArgumentException("no suitable certificate stored in chain");
  }

  private byte[] sendCertificateRequest(String messageId, String returnUrl, byte[] certReq)
    throws GovManagementException
  {
    byte[] obtainedCert = null;
    try
    {
      PKIServiceConnector.getContextLock();
      TermAuthServiceWrapper wrapper = createWrapper();
      obtainedCert = wrapper.requestCertificate(certReq, messageId, returnUrl);
    }
    finally
    {
      PKIServiceConnector.releaseContextLock();
    }

    if (returnUrl != null)
    {
      facade.storeCVCRequestSent(entityId);
    }
    return obtainedCert;
  }

  /**
   * Fetches a new Certificate Description form the Service of the CA.
   */
  private byte[] fetchCertDescription(byte[] cvc) throws GovManagementException, IOException
  {
    byte[] obtainedCert = null;
    ECCVCertificate atcvc = new ECCVCertificate(cvc);
    byte[] certificateDescriptionHash = atcvc.getChildElementByPath(ECCVCPath.EXTENSIONS_DISCRETIONARY_DATA_CERTIFICATE_DESCRIPTION_HASH)
                                             .getValue();
    try
    {
      PKIServiceConnector.getContextLock();
      DvcaCertDescriptionWrapper wrapper = createCertDescriptionWrapper();
      obtainedCert = wrapper.getCertificateDescription(certificateDescriptionHash);
    }
    finally
    {
      PKIServiceConnector.releaseContextLock();
    }
    return obtainedCert;
  }

  private TermAuthServiceWrapper createWrapper() throws GovManagementException
  {
    PkiServiceType serviceData = pkiConfig.getTerminalAuthService();
    String serviceUrl = serviceData.getUrl();
    SslKeysDto keys = pkiConfig.getSslKeys().get(serviceData.getSslKeysId());
    String wsdlVersion = policy.getWsdlVersionTerminalAuth();

    try
    {
      PKIServiceConnector connector = new PKIServiceConnector(600, keys.getServerCertificate(),
                                                              keys.getClientKey(),
                                                              keys.getClientCertificateChain(), entityId);
      return ServiceWrapperFactory.createTermAuthServiceWrapper(connector, serviceUrl, wsdlVersion);
    }
    catch (GeneralSecurityException e)
    {
      LOG.error(entityId + ": problem with crypto data", e);
      throw new GovManagementException(GlobalManagementCodes.EC_UNEXPECTED_ERROR, e.getMessage());
    }
    catch (URISyntaxException e)
    {
      LOG.error(entityId + ": problem with crypto data", e);
      throw new GovManagementException(IDManagementCodes.INVALID_URL, "ID.value.service.termAuth.url");
    }
  }

  private DvcaCertDescriptionWrapper createCertDescriptionWrapper() throws GovManagementException
  {
    PkiServiceType serviceData = pkiConfig.getDvcaCertDescriptionService();
    String serviceUrl = serviceData.getUrl();
    SslKeysDto keys = pkiConfig.getSslKeys().get(serviceData.getSslKeysId());
    String wsdlVersion = policy.getWsdlVersionTerminalAuth();

    try
    {
      PKIServiceConnector connector = new PKIServiceConnector(600, keys.getServerCertificate(),
                                                              keys.getClientKey(),
                                                              keys.getClientCertificateChain(), entityId);
      return ServiceWrapperFactory.createDvcaCertDescriptionWrapper(connector, serviceUrl, wsdlVersion);
    }
    catch (GeneralSecurityException e)
    {
      LOG.error(entityId + ": problem with crypto data", e);
      throw new GovManagementException(GlobalManagementCodes.EC_UNEXPECTED_ERROR, e.getMessage());
    }
    catch (URISyntaxException e)
    {
      LOG.error(entityId + ": problem with crypto data", e);
      throw new GovManagementException(IDManagementCodes.INVALID_URL, "ID.value.service.termAuth.url");
    }
  }

  /**
   * This method returns an ordered chain with the DVCA certificate at position 0 and then the chain to the
   * first root certificate, we will remove any double certificates and add some missing certificates from the
   * chain if it is possible.
   *
   * @param preferedCHR The CHR which should be the DVCA, use null to get the most recent version
   * @return
   * @throws GovManagementException
   */
  private byte[][] getCACertificates(String preferedCHR) throws GovManagementException
  {
    byte[][] result;
    try
    {
      PKIServiceConnector.getContextLock();
      LOG.debug(entityId + ": obtained lock on SSL context for downloading CACerts");
      TermAuthServiceWrapper wrapper = createWrapper();
      result = wrapper.getCACertificates();
    }
    catch (WebServiceException e)
    {
      LOG.error(entityId + ": cannot get certificates", e);
      throw new GovManagementException(GlobalManagementCodes.EC_UNEXPECTED_ERROR, e.getMessage());
    }
    finally
    {
      PKIServiceConnector.releaseContextLock();
    }
    // chain contains all the certificates we got, when we got one certificate more than once, take the one
    // which was not self singed. We will use that map later on to fetch the certificates we need.
    Map<String, TerminalData> chain = new HashMap<>();
    // signedBy contains all the CAR, so we cna find the certificate which did not sign anything, this is
    // probably the DVCA cert.
    Set<String> signedBy = new HashSet<>();
    for ( byte[] cert : result )
    {
      try
      {
        TerminalData cvc = new TerminalData(cert);
        TerminalData original = chain.get(cvc.getHolderReferenceString());
        if (original == null || (original.isSelfSigned() && !cvc.isSelfSigned()))
        {
          chain.put(cvc.getHolderReferenceString(), cvc);
          signedBy.add(cvc.getCAReferenceString());
        }
      }
      catch (IOException e)
      {
        throw new IllegalArgumentException("unable to parse given cvc", e);
      }
    }

    if (preferedCHR != null && chain.containsKey(preferedCHR))
    {
      return createOrderedCertChain(chain, preferedCHR);
    }
    Set<String> notSigningCVCs = new HashSet<>(chain.keySet());
    notSigningCVCs.removeAll(signedBy);
    if (notSigningCVCs.isEmpty())
    {

      LOG.error(entityId
                + ": Can not find the DVCA certificate in the GetCACertificates output, we searched for a certificate which is not used to sign any other certificate.");
      return result;
    }
    else if (notSigningCVCs.size() == 1)
    {
      return createOrderedCertChain(chain, notSigningCVCs.iterator().next());
    }
    else
    {
      LOG.error(entityId
                + ": It looks like there is more than one DVCA certificate in the GetCACertificates output, use the newest one: "
                + notSigningCVCs);
      Date issued = new Date(0);
      String recentCHR = null;
      for ( String chr : notSigningCVCs )
      {
        TerminalData cvc = chain.get(chr);
        if (issued.before(cvc.getEffectiveDate()))
        {
          issued = cvc.getEffectiveDate();
          recentCHR = chr;
        }
      }
      return createOrderedCertChain(chain, recentCHR);
    }
  }

  /**
   * Return an array with the CVC with the CHR topCHR at position 0 and then all the following certs.
   *
   * @param chain All the certs we know so we can fetch them from this map.
   * @param topCHR the CVC to start with.
   * @return
   */
  private byte[][] createOrderedCertChain(Map<String, TerminalData> chain, String topCHR)
  {
    LinkedList<byte[]> cvcChain = new LinkedList<>();
    TerminalData cvc = null;
    String recentCHR = topCHR;
    do
    {
      cvc = chain.get(recentCHR);
      if (cvc == null)
      {
        cvc = KNOWN_ROOT_CERTS.get(recentCHR);
      }
      if (cvc == null)
      {
        LOG.error(entityId + ": can not find CVC with CHR: " + recentCHR);
        break;
      }
      cvcChain.addLast(cvc.getEncoded());
      recentCHR = new String(cvc.getCAReference(), StandardCharsets.UTF_8);
    }
    while (!cvc.isSelfSigned());

    return cvcChain.toArray(new byte[cvcChain.size()][]);
  }

  private void storeRequestData(String primaryKey,
                                String messageId,
                                CertificateRequest certificateRequest,
                                CertificateDescription description,
                                byte[][] chain,
                                byte[] pkcs8PrivateKey)
  {
    facade.storeCVCRequestCreated(primaryKey,
                                  messageId,
                                  certificateRequest.getEncoded(),
                                  description == null ? null : description.getEncoded(),
                                  chain,
                                  pkcs8PrivateKey);
  }

  private static boolean canBeGivenToRequestGenerator(byte[] data)
  {
    try
    {
      ECCVCertificate cert = new ECCVCertificate(data);
      return cert.getChildElementByPath(ECCVCPath.PUBLIC_KEY_PRIME_MODULUS) != null;
    }
    catch (IOException e)
    {
      return false;
    }
  }

  /**
   * Deletes a pending certificate request from database.
   *
   * @return management message
   */
  ManagementMessage deletePendingRequest()
  {
    facade.deleteCVCRequest(entityId);
    return GlobalManagementCodes.OK.createMessage();
  }
}
