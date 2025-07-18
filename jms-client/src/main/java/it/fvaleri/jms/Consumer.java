package it.fvaleri.jms;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;

public class Consumer extends Client implements ExceptionListener {
    private Connection connection;
    private Session session;
    private MessageConsumer consumer;

    public Consumer(String threadName) {
        super(threadName);
    }

    @Override
    public void execute() throws Exception {
        connection = connect();
        connection.setExceptionListener(this);
        // non-transacted and AUTO_ACKNOWLEDGE (the message is acked only when onMessage completes successfully)
        session = connection.createSession(Configuration.ENABLE_TXN, Session.AUTO_ACKNOWLEDGE);
        Destination destination = createDestination(session);
        consumer = createConsumer(session, destination);
        connection.start();
        System.out.printf("Consuming from %s%n", destination);
        while (!closed.get() && messageCount.get() < Configuration.NUM_MESSAGES) {
            try {
                Message message = consumer.receive(Configuration.RECEIVE_TIMEOUT_MS);
                System.out.printf("Message received %s", message.getJMSRedelivered() ? "(redelivered)" : "");
                sleep(Configuration.PROCESSING_DELAY_MS);
                messageCount.incrementAndGet();
            } catch (Exception e) {
                System.err.println(e.getMessage());
                if (Configuration.ENABLE_TXN) {
                    rollbackBatch(session);
                }
                if (!retriable(e)) {
                    shutdown(e);
                }
            }
        }
    }

    @Override
    public void onShutdown() {
        if (Configuration.ENABLE_TXN) {
            maybeCommitBatch(session, messageCount.get());
        }
        try {
            consumer.close();
            session.close();
            connection.close();
        } catch (JMSException e) {
        }
    }

    @Override
    public void onException(JMSException e) {
        System.err.println(e.getMessage());
        if (Configuration.ENABLE_TXN) {
            rollbackBatch(session);
        }
    }
}
