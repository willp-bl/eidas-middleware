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

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.Arrays;

import javax.xml.bind.DatatypeConverter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import de.governikus.eumw.eidascommon.Utils;
import de.governikus.eumw.poseidas.config.schema.PkiServiceType;
import de.governikus.eumw.poseidas.gov2server.GovManagementException;
import de.governikus.eumw.poseidas.gov2server.constants.admin.GlobalManagementCodes;
import de.governikus.eumw.poseidas.gov2server.constants.admin.IDManagementCodes;
import de.governikus.eumw.poseidas.server.idprovider.accounting.SNMPDelegate;
import de.governikus.eumw.poseidas.server.idprovider.accounting.SNMPDelegate.OID;
import de.governikus.eumw.poseidas.server.idprovider.config.EPAConnectorConfigurationDto;
import de.governikus.eumw.poseidas.server.idprovider.config.SslKeysDto;
import de.governikus.eumw.poseidas.server.pki.caserviceaccess.PKIServiceConnector;
import de.governikus.eumw.poseidas.server.pki.caserviceaccess.PassiveAuthServiceWrapper;
import de.governikus.eumw.poseidas.server.pki.caserviceaccess.ServiceWrapperFactory;


/**
 * Process of getting and storing the master and defect lists.
 * 
 * @author tautenhahn, hme
 */
public class MasterAndDefectListHandler extends BerCaRequestHandlerBase
{

  private static final Log LOG = LogFactory.getLog(MasterAndDefectListHandler.class);

  /**
   * Create new instance for current configuration
   * 
   * @param facade must be obtained by client
   */
  MasterAndDefectListHandler(EPAConnectorConfigurationDto nPaConf, TerminalPermissionAO facade)
    throws GovManagementException
  {
    super(nPaConf, facade);
  }

  /**
   * Get and
   * 
   * @throws GovManagementException
   */
  void updateLists() throws GovManagementException
  {
    byte[] masterList = null;
    byte[] defectList = null;

    BerCaPolicy policy = PolicyImplementationFactory.getInstance().getPolicy(pkiConfig.getBerCaPolicyId());
    if (policy.hasPassiveAuthService())
    {
      try
      {
        PKIServiceConnector.getContextLock();
        LOG.debug(entityId + ": obtained lock on SSL context for downloading master and defect list");
        PassiveAuthServiceWrapper wrapper = createWrapper();
        masterList = getMasterList(wrapper);
        defectList = getDefectList(wrapper);
        if (LOG.isDebugEnabled())
        {
          if (masterList != null)
          {
            LOG.debug("MasterList:\n"
                      + Utils.breakAfter76Chars(DatatypeConverter.printBase64Binary(masterList)));
          }
          if (defectList != null)
          {
            LOG.debug("DefectList:\n"
                      + Utils.breakAfter76Chars(DatatypeConverter.printBase64Binary(defectList)));
          }
        }
      }
      catch (MalformedURLException e)
      {
        LOG.error(entityId + ": Can not parse service URL", e);
        throw new GovManagementException(GlobalManagementCodes.INTERNAL_ERROR);
      }
      catch (GovManagementException e)
      {
        throw e;
      }
      catch (Throwable t)
      {
        LOG.error(entityId + ": cannot renew master and defect list", t);
        throw new GovManagementException(GlobalManagementCodes.INTERNAL_ERROR);
      }
      finally
      {
        PKIServiceConnector.releaseContextLock();
      }
    }
    else
    {
      try
      {
        // unsupported master and defect list service so we "emulate" it
        TerminalPermission tp = facade.getTerminalPermission(entityId);
        masterList = Arrays.copyOf(tp.getMasterList(), tp.getMasterList().length);
        defectList = Arrays.copyOf(tp.getDefectList(), tp.getDefectList().length);
      }
      catch (Throwable t)
      {
        LOG.error(entityId + ": cannot fetch master and defect list", t);
        throw new GovManagementException(GlobalManagementCodes.INTERNAL_ERROR);
      }
    }
    if (masterList != null)
    {
      facade.storeMasterList(entityId, masterList);
    }
    if (defectList != null)
    {
      facade.storeDefectList(entityId, defectList);
    }
  }

  private PassiveAuthServiceWrapper createWrapper() throws GovManagementException
  {
    PkiServiceType serviceData = pkiConfig.getPassiveAuthService();
    String serviceUrl = serviceData.getUrl();
    SslKeysDto keys = pkiConfig.getSslKeys().get(serviceData.getSslKeysId());
    String version = PolicyImplementationFactory.getInstance()
                                                .getPolicy(pkiConfig.getBerCaPolicyId())
                                                .getWsdlVersionPassiveAuth();

    try
    {
      PKIServiceConnector connector = new PKIServiceConnector(60, keys.getServerCertificate(),
                                                              keys.getClientKey(),
                                                              keys.getClientCertificateChain(), entityId);
      return ServiceWrapperFactory.createPassiveAuthServiceWrapper(connector, serviceUrl, version);
    }
    catch (GeneralSecurityException e)
    {
      LOG.error(entityId + ": problem with crypto data of SP for cvcRefID " + entityId, e);
      throw new GovManagementException(GlobalManagementCodes.EC_UNEXPECTED_ERROR, e.getMessage());
    }
    catch (URISyntaxException e)
    {
      throw new GovManagementException(IDManagementCodes.INVALID_URL, "ID.value.service.termAuth.url");
    }
  }

  private byte[] getMasterList(PassiveAuthServiceWrapper wrapper) throws MalformedURLException
  {
    byte[] masterList = wrapper.getMasterList();
    if (!isLocalZip(masterList))
    {
      CmsSignatureChecker checker = new CmsSignatureChecker(pkiConfig.getMasterListTrustAnchor());
      if (!checker.checkEnvelopedSignature(masterList, entityId))
      {
        SNMPDelegate.getInstance()
                    .sendSNMPTrap(OID.MASTERLIST_SIGNATURE_WRONG,
                                  SNMPDelegate.MASTERLIST_SIGNATURE_WRONG + " "
                                                                  + "signature check for master list failed");
        return null;
      }
    }
    return masterList;
  }

  private byte[] getDefectList(PassiveAuthServiceWrapper wrapper) throws MalformedURLException
  {

    byte[] defectList = wrapper.getDefectList();
    if (!isLocalZip(defectList))
    {
      CmsSignatureChecker checker = new CmsSignatureChecker(pkiConfig.getDefectListTrustAnchor());
      if (!checker.checkEnvelopedSignature(defectList, entityId))
      {
        SNMPDelegate.getInstance()
                    .sendSNMPTrap(OID.DEFECTLIST_SIGNATURE_WRONG,
                                  SNMPDelegate.DEFECTLIST_SIGNATURE_WRONG + " "
                                                                  + "signature check for defect list failed");
        return null;
      }
    }
    return defectList;
  }

  private boolean isLocalZip(byte[] data) throws MalformedURLException
  {
    String host = new URL(pkiConfig.getPassiveAuthService().getUrl()).getHost();
    return data.length >= 2 && data[0] == 0x50 && data[1] == 0X4b
           && ("localhost".equals(host) || "127.0.0.1".equals(host));
  }
}
