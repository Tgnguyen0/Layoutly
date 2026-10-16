package com.tgnguyen.layoutlybe.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tgnguyen.layoutlybe.model.snapshot.FigmaSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class FixtureServiceTest {
    @TempDir Path temp;
    @Test void existingRawAndGoldenCannotBeOverwrittenEvenBySanitizedNameCollision() throws Exception {
        var service=new FixtureService(new ObjectMapper().findAndRegisterModules(),new DesignStructureService(),temp.toString());
        String raw="{\"document\":{\"id\":\"root\",\"type\":\"FRAME\",\"children\":[]}}";
        var snapshot=new FigmaSnapshot("id","file","name","v1",null,Instant.now(),raw,new FigmaParserService().parseDesignTree(raw),List.of());
        var bundle=new AssetExportService.AssetBundle(Map.of(),Map.of());
        Path saved=service.save("My Case",snapshot,bundle);
        Files.writeString(saved.resolve("reference.png"),"reviewed-golden-bytes");
        assertThatThrownBy(()->service.save("my-case",snapshot,bundle)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("da ton tai");
        assertThat(Files.readString(saved.resolve("reference.png"))).isEqualTo("reviewed-golden-bytes");
        assertThat(Files.readString(saved.resolve("figma-raw.json"))).isEqualTo(raw);
    }
}
