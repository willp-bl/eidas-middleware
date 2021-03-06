/*
 * Copyright (c) 2018 Governikus KG. Licensed under the EUPL, Version 1.2 or as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work except
 * in compliance with the Licence. You may obtain a copy of the Licence at:
 * http://joinup.ec.europa.eu/software/page/eupl Unless required by applicable law or agreed to in writing,
 * software distributed under the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS
 * OF ANY KIND, either express or implied. See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package de.governikus.eumw.poseidas.cardserver.eac.functions.genkeypair;

import de.governikus.eumw.poseidas.cardserver.eac.functions.FunctionParameter;


/**
 * Parameter for key generation.
 * 
 * @author Arne Stahlbock, arne.stahlbock@governikus.com
 */
public class GenerateKeyPairParameter implements FunctionParameter
{

  /**
   * Key ID.
   */
  private byte[] keyID = null;

  /**
   * Constructor.
   * 
   * @param keyID key ID
   */
  public GenerateKeyPairParameter(byte[] keyID)
  {
    super();
    this.keyID = keyID;
  }

  /**
   * Get key ID.
   * 
   * @return
   */
  public byte[] getKeyID()
  {
    return this.keyID;
  }
}
