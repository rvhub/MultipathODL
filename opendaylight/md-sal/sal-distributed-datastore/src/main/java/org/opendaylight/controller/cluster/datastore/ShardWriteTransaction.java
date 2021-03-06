/*
 *
 *  Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 which accompanies this distribution,
 *  and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 */

package org.opendaylight.controller.cluster.datastore;

import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import org.opendaylight.controller.cluster.datastore.messages.CloseTransaction;
import org.opendaylight.controller.cluster.datastore.messages.CloseTransactionReply;
import org.opendaylight.controller.cluster.datastore.messages.DeleteData;
import org.opendaylight.controller.cluster.datastore.messages.MergeData;
import org.opendaylight.controller.cluster.datastore.messages.ReadyTransaction;
import org.opendaylight.controller.cluster.datastore.messages.WriteData;
import org.opendaylight.controller.sal.core.spi.data.DOMStoreTransactionChain;
import org.opendaylight.controller.sal.core.spi.data.DOMStoreWriteTransaction;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

/**
 * @author: syedbahm
 * Date: 8/6/14
 */
public class ShardWriteTransaction extends ShardTransaction {
  private final DOMStoreWriteTransaction transaction;
  private final LoggingAdapter log =
      Logging.getLogger(getContext().system(), this);
  public ShardWriteTransaction(DOMStoreWriteTransaction transaction, ActorRef shardActor, SchemaContext schemaContext) {
    super( shardActor, schemaContext);
    this.transaction = transaction;

  }

  public ShardWriteTransaction(DOMStoreTransactionChain transactionChain, DOMStoreWriteTransaction transaction, ActorRef shardActor, SchemaContext schemaContext) {
    super(transactionChain, shardActor, schemaContext);
    this.transaction = transaction;
  }

  @Override
  public void handleReceive(Object message) throws Exception {
    if (WriteData.SERIALIZABLE_CLASS.equals(message.getClass())) {
      writeData(transaction, WriteData.fromSerializable(message, schemaContext));
    } else if (MergeData.SERIALIZABLE_CLASS.equals(message.getClass())) {
      mergeData(transaction, MergeData.fromSerializable(message, schemaContext));
    } else if (DeleteData.SERIALIZABLE_CLASS.equals(message.getClass())) {
      deleteData(transaction,DeleteData.fromSerializable(message));
    } else if (ReadyTransaction.SERIALIZABLE_CLASS.equals(message.getClass())) {
      readyTransaction(transaction,new ReadyTransaction());
    }else {
      super.handleReceive(message);
    }
  }

  protected void closeTransaction(CloseTransaction message) {
    transaction.close();
    getSender().tell(new CloseTransactionReply().toSerializable(), getSelf());
    getSelf().tell(PoisonPill.getInstance(), getSelf());
  }

    /**
     * The following method is used in unit testing only
     * hence the default scope.
     * This is done to test out failure cases.
     */
    public void forUnitTestOnlyExplicitTransactionClose() {
        transaction.close();
    }
}
