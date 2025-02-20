package com.provectus.kafka.ui.controller;

import com.provectus.kafka.ui.api.MessagesApi;
import com.provectus.kafka.ui.model.ConsumerPosition;
import com.provectus.kafka.ui.model.CreateTopicMessageDTO;
import com.provectus.kafka.ui.model.SeekDirectionDTO;
import com.provectus.kafka.ui.model.SeekTypeDTO;
import com.provectus.kafka.ui.model.TopicMessageEventDTO;
import com.provectus.kafka.ui.model.TopicMessageSchemaDTO;
import com.provectus.kafka.ui.service.ClusterService;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.kafka.common.TopicPartition;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@Log4j2
public class MessagesController implements MessagesApi {
  private final ClusterService clusterService;

  @Override
  public Mono<ResponseEntity<Void>> deleteTopicMessages(
      String clusterName, String topicName, @Valid List<Integer> partitions,
      ServerWebExchange exchange) {
    return clusterService.deleteTopicMessages(
        clusterName,
        topicName,
        Optional.ofNullable(partitions).orElse(List.of())
    ).map(ResponseEntity::ok);
  }

  @Override
  public Mono<ResponseEntity<Flux<TopicMessageEventDTO>>> getTopicMessages(
      String clusterName, String topicName, @Valid SeekTypeDTO seekType, @Valid List<String> seekTo,
      @Valid Integer limit, @Valid String q, @Valid SeekDirectionDTO seekDirection,
      ServerWebExchange exchange) {
    return parseConsumerPosition(topicName, seekType, seekTo, seekDirection)
        .map(position ->
            ResponseEntity.ok(
                clusterService.getMessages(clusterName, topicName, position, q, limit)
            )
        );
  }

  @Override
  public Mono<ResponseEntity<TopicMessageSchemaDTO>> getTopicSchema(
      String clusterName, String topicName, ServerWebExchange exchange) {
    return Mono.just(clusterService.getTopicSchema(clusterName, topicName))
        .map(ResponseEntity::ok);
  }

  @Override
  public Mono<ResponseEntity<Void>> sendTopicMessages(
      String clusterName, String topicName, @Valid Mono<CreateTopicMessageDTO> createTopicMessage,
      ServerWebExchange exchange) {
    return createTopicMessage.flatMap(msg ->
        clusterService.sendMessage(clusterName, topicName, msg)
    ).map(ResponseEntity::ok);
  }


  private Mono<ConsumerPosition> parseConsumerPosition(
      String topicName, SeekTypeDTO seekType, List<String> seekTo,
      SeekDirectionDTO seekDirection) {
    return Mono.justOrEmpty(seekTo)
        .defaultIfEmpty(Collections.emptyList())
        .flatMapIterable(Function.identity())
        .map(p -> {
          String[] split = p.split("::");
          if (split.length != 2) {
            throw new IllegalArgumentException(
                "Wrong seekTo argument format. See API docs for details");
          }

          return Pair.of(
              new TopicPartition(topicName, Integer.parseInt(split[0])),
              Long.parseLong(split[1])
          );
        })
        .collectMap(Pair::getKey, Pair::getValue)
        .map(positions -> new ConsumerPosition(seekType != null ? seekType : SeekTypeDTO.BEGINNING,
            positions, seekDirection));
  }

}
