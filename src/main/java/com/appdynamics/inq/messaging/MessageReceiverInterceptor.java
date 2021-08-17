package com.appdynamics.inq.messaging;

import com.appdynamics.agent.api.AppdynamicsAgent;
import com.appdynamics.agent.api.EntryTypes;
import com.appdynamics.agent.api.Transaction;
import com.appdynamics.instrumentation.sdk.Rule;
import com.appdynamics.instrumentation.sdk.SDKClassMatchType;
import com.appdynamics.instrumentation.sdk.toolbox.reflection.IReflector;

import java.util.ArrayList;
import java.util.List;

public class MessageReceiverInterceptor extends MyBaseInterceptor {
    IReflector setProperty, getMessageTypeStr, getPropertyAsString; //com.inq.messaging.messages.Message
    IReflector keyChatId;

    public MessageReceiverInterceptor() {
        super();
        setProperty = makeInvokeInstanceMethodReflector("setProperty", String.class.getCanonicalName(), String.class.getCanonicalName() );
        getMessageTypeStr = makeInvokeInstanceMethodReflector("getMessageTypeStr" );
        getPropertyAsString = makeInvokeInstanceMethodReflector("getPropertyAsString", String.class.getCanonicalName());
        keyChatId = getNewReflectionBuilder().loadClass("com.inq.chatcommerce.MessageFields").accessFieldValue("KEY_CHAT_ID", true).build();
    }

    @Override
    public Object onMethodBegin(Object objectIntercepted, String className, String methodName, Object[] params) {
        Object message = params[0];
        String correlationHeader = (String) getReflectiveObject(message, getPropertyAsString, CORRELATION_HEADER_KEY);
        String messageType = getReflectiveString(message, getMessageTypeStr, "UNKNOWN-TYPE");
        Transaction transaction = AppdynamicsAgent.startTransactionAndServiceEndPoint("BT-ApacheTribesMessage-"+ messageType, correlationHeader, "ApacheTribesMessage-"+messageType, EntryTypes.POJO, true);
        /*
        try {
            Object chatIdKey = getReflectiveObject(message, keyChatId);
            String chatId = (String) getReflectiveObject(message, getPropertyAsString, chatIdKey);
            transaction.collectData("ChatID", chatId, dataScopes);
        } catch (Exception e) {
            getLogger().info("Error getting chat id: "+e);
        }

         */
        transaction.collectData("Message-Type", messageType, dataScopes);

        return transaction;
    }

    @Override
    public void onMethodEnd(Object state, Object object, String className, String methodName, Object[] params, Throwable exception, Object returnVal) {
        if( state == null ) return;
        Transaction transaction = (Transaction) state;
        if( exception != null ) transaction.markAsError(exception.getMessage());
        //if( methodName.equals("processTimedOutMessage") ) transaction.markAsError("Processing Timed Out Message");
        transaction.end();
    }

    @Override
    public List<Rule> initializeRules() {
        List<Rule> rules = new ArrayList<Rule>();

        rules.add(new Rule.Builder(
                "com.inq.messaging.handlers.MessageHandler")
                .classMatchType(SDKClassMatchType.IMPLEMENTS_INTERFACE)
                .methodMatchString("processMessage")
                .build()
        );
        rules.add(new Rule.Builder(
                "com.inq.messaging.handlers.MessageRouter")
                .classMatchType(SDKClassMatchType.MATCHES_CLASS)
                .methodMatchString("processTimedOutMessage")
                .build()
        );
        return rules;
    }
}
