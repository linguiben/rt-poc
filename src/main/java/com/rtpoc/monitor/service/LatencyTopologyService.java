package com.rtpoc.monitor.service;

import com.rtpoc.monitor.api.TopologyLink;
import com.rtpoc.monitor.api.TopologyNode;
import com.rtpoc.monitor.api.TopologySnapshot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;

@Service
public class LatencyTopologyService {

  public static final long REFRESH_INTERVAL_MS = 2_400L;

  private static final List<String> SITE_ORDER = List.of("TKO1", "TKO2", "SKM");
  private static final List<Integer> SOURCE_OFFSETS = List.of(-140, -70, 0, 70, 140);
  private static final List<String> SOURCE_NAMES = List.of("RT", "BB", "OMDC", "CIIS", "BJ");
  private static final List<String> STAGE_NAMES = List.of("L1", "L2", "L3", "BG", "HCS");

  private final Random random = new Random();
  private final AtomicReference<TopologySnapshot> currentSnapshot = new AtomicReference<>();
  private final List<NodeTemplate> nodeTemplates;
  private final List<LinkTemplate> linkTemplates;

  public LatencyTopologyService() {
    this.nodeTemplates = buildNodeTemplates();
    this.linkTemplates = buildLinkTemplates();
    this.currentSnapshot.set(buildSnapshot());
  }

  public TopologySnapshot currentSnapshot() {
    return currentSnapshot.get();
  }

  public TopologySnapshot refreshSnapshot() {
    TopologySnapshot snapshot = buildSnapshot();
    currentSnapshot.set(snapshot);
    return snapshot;
  }

  private TopologySnapshot buildSnapshot() {
    List<TopologyNode> nodes = nodeTemplates.stream()
        .map(this::toNode)
        .toList();
    List<TopologyLink> links = linkTemplates.stream()
        .map(this::toLink)
        .toList();
    return new TopologySnapshot("TKO2", REFRESH_INTERVAL_MS, Instant.now(), nodes, links);
  }

  private TopologyNode toNode(NodeTemplate template) {
    if ("source".equals(template.type())) {
      int rate = jitter(template.rateBase(), 40, 700);
      return new TopologyNode(
          template.id(),
          template.site(),
          template.name(),
          template.type(),
          template.x(),
          template.y(),
          rate,
          null,
          null,
          null,
          null,
          null,
          null,
          null
      );
    }

    int scale = "L3".equals(template.name()) ? 12 : 7;
    int avg = jitter(template.avgBase(), scale, 2);
    int p95 = jitter(template.p95Base(), (int) Math.round(scale * 1.5), avg + 3);
    int cpu = clamp(jitter(template.cpuBase(), (int) Math.round(scale * 1.7), 8), 8, 96);
    int mem = clamp(jitter(template.memBase(), (int) Math.round(scale * 1.4), 10), 10, 98);
    int messages = template.messagesBase() == null
        ? 0
        : jitter(template.messagesBase(), 12_000, 100_000);

    return new TopologyNode(
        template.id(),
        template.site(),
        template.name(),
        template.type(),
        template.x(),
        template.y(),
        null,
        avg,
        p95,
        cpu,
        cpu,
        mem,
        template.kind(),
        template.messagesBase() == null ? null : messages
    );
  }

  private TopologyLink toLink(LinkTemplate template) {
    int swing = switch (template.kind()) {
      case "cross" -> 18;
      case "feedback" -> 14;
      default -> 6;
    };
    int current = jitter(template.anchor(), swing, template.base());
    return new TopologyLink(
        template.id(),
        template.from(),
        template.to(),
        template.base(),
        template.group(),
        template.kind(),
        current
    );
  }

  private int jitter(int base, int range, int floor) {
    int value = (int) Math.round(base + (random.nextDouble() - 0.5d) * range);
    return Math.max(floor, value);
  }

  private int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }

  private List<NodeTemplate> buildNodeTemplates() {
    Map<String, TowerSpec> towers = Map.of(
        "TKO1", new TowerSpec(292, 292),
        "TKO2", new TowerSpec(930, 930),
        "SKM", new TowerSpec(1568, 1568)
    );
    Map<String, Integer> levelY = Map.of(
        "sources", 156,
        "l1", 350,
        "l2", 510,
        "l3", 670,
        "bg", 847,
        "hcs", 1007
    );
    Map<String, Map<String, StageSeed>> stageSeeds = buildStageSeeds();
    List<NodeTemplate> nodes = new ArrayList<>();

    for (String site : SITE_ORDER) {
      TowerSpec tower = towers.get(site);
      for (int idx = 0; idx < SOURCE_NAMES.size(); idx++) {
        String sourceName = SOURCE_NAMES.get(idx);
        int baseRate = switch (site) {
          case "TKO2" -> 940 - idx * 20;
          case "SKM" -> 900 - idx * 17;
          default -> 890 - idx * 18;
        };
        nodes.add(new NodeTemplate(
            site + "-" + sourceName,
            site,
            sourceName,
            "source",
            tower.x() + SOURCE_OFFSETS.get(idx),
            levelY.get("sources"),
            baseRate,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        ));
      }
    }

    for (String site : SITE_ORDER) {
      TowerSpec tower = towers.get(site);
      for (String stage : STAGE_NAMES) {
        StageSeed seed = stageSeeds.get(site).get(stage);
        nodes.add(new NodeTemplate(
            site + "-" + stage,
            site,
            stage,
            "stage",
            tower.stageX(),
            levelY.get(stage.toLowerCase()),
            null,
            seed.avg(),
            seed.p95(),
            seed.cpu(),
            seed.cpu(),
            seed.mem(),
            seed.kind(),
            seed.messages()
        ));
      }
    }

    return List.copyOf(nodes);
  }

  private Map<String, Map<String, StageSeed>> buildStageSeeds() {
    Map<String, Map<String, StageSeed>> seeds = new LinkedHashMap<>();

    Map<String, StageSeed> tko1 = new LinkedHashMap<>();
    tko1.put("L1", new StageSeed(29, 42, 38, 44, "flow", null));
    tko1.put("L2", new StageSeed(33, 49, 42, 49, "process", null));
    tko1.put("L3", new StageSeed(61, 126, 70, 76, "process", null));
    tko1.put("BG", new StageSeed(18, 35, 24, 31, "process", 182_340));
    tko1.put("HCS", new StageSeed(3, 6, 10, 18, "flow", null));
    seeds.put("TKO1", tko1);

    Map<String, StageSeed> tko2 = new LinkedHashMap<>();
    tko2.put("L1", new StageSeed(31, 45, 40, 46, "flow", null));
    tko2.put("L2", new StageSeed(35, 56, 47, 55, "process", null));
    tko2.put("L3", new StageSeed(74, 146, 82, 87, "process", null));
    tko2.put("BG", new StageSeed(22, 38, 31, 39, "process", 241_680));
    tko2.put("HCS", new StageSeed(3, 5, 12, 20, "flow", null));
    seeds.put("TKO2", tko2);

    Map<String, StageSeed> skm = new LinkedHashMap<>();
    skm.put("L1", new StageSeed(30, 44, 36, 42, "flow", null));
    skm.put("L2", new StageSeed(34, 52, 43, 50, "process", null));
    skm.put("L3", new StageSeed(63, 134, 72, 78, "process", null));
    skm.put("BG", new StageSeed(19, 36, 25, 33, "process", 193_120));
    skm.put("HCS", new StageSeed(3, 6, 11, 19, "flow", null));
    seeds.put("SKM", skm);

    return seeds;
  }

  private List<LinkTemplate> buildLinkTemplates() {
    List<LinkTemplate> links = new ArrayList<>();

    for (String site : SITE_ORDER) {
      for (String source : SOURCE_NAMES) {
        addLink(links, site + "-" + source, site + "-L1", 30, "sources", "intra", 31);
      }
      int anchor = switch (site) {
        case "TKO2" -> 35;
        case "SKM" -> 34;
        default -> 33;
      };
      addLink(links, site + "-L1", site + "-L2", 30, "l1-l2", "intra", anchor);
    }

    addLink(links, "TKO1-L2", "TKO1-L3", 50, "l2-l3", "intra", 118);
    addLink(links, "SKM-L2", "SKM-L3", 50, "l2-l3", "intra", 124);
    addLink(links, "TKO1-L2", "TKO2-L3", 50, "l2-l3", "cross", 168);
    addLink(links, "SKM-L2", "TKO2-L3", 50, "l2-l3", "cross", 174);
    addLink(links, "TKO2-L2", "TKO1-L3", 50, "l2-l3", "cross", 196);
    addLink(links, "TKO2-L2", "SKM-L3", 50, "l2-l3", "cross", 182);
    addLink(links, "TKO2-L2", "TKO2-L3", 50, "l2-l3", "intra", 144);

    addLink(links, "TKO2-L3", "TKO1-BG", 30, "l3-bg", "cross", 92);
    addLink(links, "TKO2-L3", "SKM-BG", 30, "l3-bg", "cross", 86);
    addLink(links, "TKO2-L3", "TKO2-BG", 30, "l3-bg", "intra", 34);

    addLink(links, "TKO1-BG", "TKO1-L3", 30, "bg-l3-feedback", "feedback", 56);
    addLink(links, "TKO1-BG", "TKO2-L3", 30, "bg-l3-feedback", "feedback", 96);
    addLink(links, "SKM-BG", "SKM-L3", 30, "bg-l3-feedback", "feedback", 58);
    addLink(links, "SKM-BG", "TKO2-L3", 30, "bg-l3-feedback", "feedback", 94);
    addLink(links, "TKO2-BG", "TKO1-L3", 30, "bg-l3-feedback", "feedback", 88);
    addLink(links, "TKO2-BG", "SKM-L3", 30, "bg-l3-feedback", "feedback", 82);
    addLink(links, "TKO2-BG", "TKO2-L3", 30, "bg-l3-feedback", "feedback", 52);

    addLink(links, "TKO1-L3", "TKO1-L1", 30, "l3-l1-feedback", "feedback", 48);
    addLink(links, "TKO1-L3", "TKO2-L1", 30, "l3-l1-feedback", "feedback", 102);
    addLink(links, "SKM-L3", "SKM-L1", 30, "l3-l1-feedback", "feedback", 52);
    addLink(links, "SKM-L3", "TKO2-L1", 30, "l3-l1-feedback", "feedback", 98);
    addLink(links, "TKO2-L3", "TKO1-L1", 30, "l3-l1-feedback", "feedback", 124);
    addLink(links, "TKO2-L3", "SKM-L1", 30, "l3-l1-feedback", "feedback", 116);
    addLink(links, "TKO2-L3", "TKO2-L1", 30, "l3-l1-feedback", "feedback", 61);

    addLink(links, "TKO1-BG", "TKO1-HCS", 30, "bg-hcs", "intra", 24);
    addLink(links, "TKO1-BG", "TKO2-HCS", 30, "bg-hcs", "cross", 72);
    addLink(links, "SKM-BG", "SKM-HCS", 30, "bg-hcs", "intra", 25);
    addLink(links, "SKM-BG", "TKO2-HCS", 30, "bg-hcs", "cross", 70);
    addLink(links, "TKO2-BG", "TKO1-HCS", 30, "bg-hcs", "cross", 78);
    addLink(links, "TKO2-BG", "SKM-HCS", 30, "bg-hcs", "cross", 74);
    addLink(links, "TKO2-BG", "TKO2-HCS", 30, "bg-hcs", "intra", 26);

    return List.copyOf(links);
  }

  private void addLink(List<LinkTemplate> links, String from, String to, int base, String group, String kind, int anchor) {
    links.add(new LinkTemplate(from + "__" + to, from, to, base, group, kind, anchor));
  }

  private record TowerSpec(int x, int stageX) {
  }

  private record StageSeed(int avg, int p95, int cpu, int mem, String kind, Integer messages) {
  }

  private record NodeTemplate(
      String id,
      String site,
      String name,
      String type,
      int x,
      int y,
      Integer rateBase,
      Integer avgBase,
      Integer p95Base,
      Integer loadBase,
      Integer cpuBase,
      Integer memBase,
      String kind,
      Integer messagesBase
  ) {
  }

  private record LinkTemplate(
      String id,
      String from,
      String to,
      int base,
      String group,
      String kind,
      int anchor
  ) {
  }
}
