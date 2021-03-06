package com.linbox.im.server.router.handlers.dispatcher;

import com.alibaba.fastjson.JSON;
import com.linbox.im.exceptions.IMConsumerException;
import com.linbox.im.exceptions.IMException;
import com.linbox.im.message.MessageType;
import com.linbox.im.message.Message;
import com.linbox.im.server.router.handlers.Handler;
import com.linbox.im.server.storage.dao.IGroupDAO;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by lrsec on 8/26/15.
 */
@Service
@Qualifier("dispatchToGroupHandler")
public class DispatchToGroupHandler implements Handler<String, String> {
    private static final Logger logger = LoggerFactory.getLogger(DispatchToGroupHandler.class);
    private final ExecutorService service = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);

    @Autowired
    private IGroupDAO groupDAO;

    @Autowired
    private SendDispatcher dispatcher;

    @Override
    public void handle(ConsumerRecord<String, String> record) {
        String json = record.value();

        try {
            final Message groupMessage = JSON.parseObject(json, Message.class);

            if (groupMessage == null) {
                throw new IMException("Message could not be parsed correctly. Message: " + json);
            }

            final String groupId = groupMessage.groupId;
            List<String> members = groupDAO.getGroupMembers(groupId);

            for(final String userId : members) {

                if(userId == null || userId.equalsIgnoreCase(groupMessage.fromUserId)) {
                    continue;
                }

                service.submit(new Runnable() {
                    @Override
                    public void run() {
                        dispatcher.dispatchToSingle(userId, groupId, groupId, MessageType.Group, groupMessage);
                    }
                });
            }
        } catch (Exception e) {
            throw new IMConsumerException(e, json);
        }
    }
}
