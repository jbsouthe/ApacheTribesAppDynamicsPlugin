package com.appdynamics.inq.messaging;

import com.appdynamics.agent.api.AppdynamicsAgent;
import com.appdynamics.agent.api.ExitCall;
import com.appdynamics.agent.api.ExitTypes;
import com.appdynamics.agent.api.Transaction;
import com.appdynamics.instrumentation.sdk.Rule;
import com.appdynamics.instrumentation.sdk.SDKClassMatchType;
import com.appdynamics.instrumentation.sdk.toolbox.reflection.IReflector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MessageSenderInterceptor extends MyBaseInterceptor{
    IReflector setProperty, getMessageTypeStr, getPropertyAsString; //com.inq.messaging.messages.Message
    IReflector getHostnameString;//com.inq.messaging.internal.InternalConnection
    IReflector keyChatId;

    public MessageSenderInterceptor() {
        super();
        setProperty = makeInvokeInstanceMethodReflector("setProperty", String.class.getCanonicalName(), String.class.getCanonicalName() );
        getMessageTypeStr = makeInvokeInstanceMethodReflector("getMessageTypeStr" );
        getPropertyAsString = makeInvokeInstanceMethodReflector("getPropertyAsString", String.class.getCanonicalName());
        getHostnameString = makeInvokeInstanceMethodReflector("getHostnameString");
        keyChatId = getNewReflectionBuilder().loadClass("com.inq.chatcommerce.MessageFields").accessFieldValue("KEY_CHAT_ID", true).build();
    }

    @Override
    public Object onMethodBegin(Object objectIntercepted, String className, String methodName, Object[] params) {
        Object message = params[0];
        Object connection = params[1];
        Transaction transaction = AppdynamicsAgent.getTransaction(); //assume active transaction
        if( isFakeTransaction(transaction) ) return null; //do nothing then
        Map<String,String> details = new HashMap<>();
        details.put("Message_Type", getReflectiveString(message, getMessageTypeStr, "UNKNOWN-TYPE"));
        details.put("Target_Host", getReflectiveString(connection, getHostnameString, "UNKNOWN-HOST"));
        ExitCall exitCall = transaction.startExitCall(details, "Apache Tribes", ExitTypes.CUSTOM_ASYNC, true);
        getReflectiveObject(message, setProperty, CORRELATION_HEADER_KEY, exitCall.getCorrelationHeader() );
        //String chatId = (String) getReflectiveObject(message, getPropertyAsString, keyChatId);
        //transaction.collectData("ChatID", chatId, dataScopes);
        return new State(transaction,exitCall);
    }

    @Override
    public void onMethodEnd(Object state, Object object, String className, String methodName, Object[] params, Throwable exception, Object returnVal) {
        if( state == null ) return; //that was quick
        Transaction transaction = ((State)state).transaction;
        ExitCall exitCall = ((State)state).exitCall;
        exitCall.end();
        if( exception != null ) transaction.markAsError(exception.getMessage());
    }

    @Override
    public List<Rule> initializeRules() {
        List<Rule> rules = new ArrayList<Rule>();

        /*
        public interface InternalMessageSender extends InternalMessageSenderMBean {
            void start();
            void stop() throws InterruptedException;
            void sendMessageToOne(Message message, InternalConnection connection) throws MissingMessageFieldsException;
            void sendMessageToSome(final Message message, final InternalConnection[] connections) throws MissingMessageFieldsException;
            void sendMessageToAll(final Message message) throws MissingMessageFieldsException;
            void stopGracefully() throws InterruptedException;
            void removeConnection(InternalConnection connection);
        }
         */
        rules.add(new Rule.Builder(
                "com.inq.messaging.internal.InternalMessageSender")
                .classMatchType(SDKClassMatchType.IMPLEMENTS_INTERFACE)
                .methodMatchString("sendMessageToOne") //TODO why not the other send message methods?
                .build()
        );
        return rules;
    }
}
