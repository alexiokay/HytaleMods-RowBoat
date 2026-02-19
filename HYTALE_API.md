# Hytale Server API Reference

Auto-generated from: Server-2026.02.18-f3b8fff95.jar
Generated: 2026-02-18T19:25:31.364786600

Run `./gradlew generateApiDocs` to refresh after Hytale updates.

---


## com.hypixel.hytale.component

- `AddReason`
- `Archetype`
- `ArchetypeChunk`
- `CommandBuffer`
- `Component`
- `ComponentAccessor`
- `ComponentRegistration`
- `ComponentRegistry`
- `ComponentRegistryProxy`
- `ComponentType`
- `DisableProcessingAssert`
- `EmptyResourceStorage`
- `Holder`
- `IComponentRegistry`
- `IResourceStorage`
- `NonSerialized`
- `NonTicking`
- `ReadWriteQuery`
- `Ref`
- `RemoveReason`
- `Resource`
- `ResourceRegistration`
- `ResourceType`
- `Store`
- `SystemGroup`
- `SystemType`
- `WeakComponentReference`

## com.hypixel.hytale.component.data

- `ForEachTaskData`

## com.hypixel.hytale.component.data.change

- `ChangeType`
- `ComponentChange`
- `DataChange`
- `ResourceChange`
- `SystemChange`
- `SystemGroupChange`
- `SystemTypeChange`

## com.hypixel.hytale.component.data.unknown

- `TempUnknownComponent`
- `UnknownComponents`

## com.hypixel.hytale.component.dependency

- `Dependency`
- `DependencyGraph`
- `Order`
- `OrderPriority`
- `RootDependency`
- `SystemDependency`
- `SystemGroupDependency`
- `SystemTypeDependency`

## com.hypixel.hytale.component.event

- `EntityEventType`
- `EventSystemType`
- `WorldEventType`

## com.hypixel.hytale.component.metric

- `ArchetypeChunkData`
- `SystemMetricData`

## com.hypixel.hytale.component

- `package-info`

## com.hypixel.hytale.component.query

- `AndQuery`
- `AnyQuery`
- `ExactArchetypeQuery`
- `NotQuery`
- `OrQuery`
- `Query`
- `ReadWriteArchetypeQuery`

## com.hypixel.hytale.component.spatial

- `KDTree`
- `MortonCode`
- `SpatialData`
- `SpatialResource`
- `SpatialStructure`
- `SpatialSystem`

## com.hypixel.hytale.component.system

- `ArchetypeChunkSystem`
- `CancellableEcsEvent`
- `DelayedSystem`
- `EcsEvent`
- `EntityEventSystem`
- `EventSystem`
- `HolderSystem`
- `ICancellableEcsEvent`
- `ISystem`
- `MetricSystem`
- `QuerySystem`
- `RefChangeSystem`
- `RefSystem`
- `StoreSystem`
- `System`
- `WorldEventSystem`

## com.hypixel.hytale.component.system.data

- `ArchetypeDataSystem`
- `EntityDataSystem`

## com.hypixel.hytale.component.system.tick

- `ArchetypeTickingSystem`
- `DelayedEntitySystem`
- `EntityTickingSystem`
- `RunWhenPausedSystem`
- `TickableSystem`
- `TickingSystem`

## com.hypixel.hytale.component.task

- `ParallelRangeTask`
- `ParallelTask`

## com.hypixel.hytale.math.vector

- `Location`
- `Transform`
- `Vector2d`
- `Vector2i`
- `Vector2l`
- `Vector3d`
- `Vector3f`
- `Vector3i`
- `Vector3l`
- `Vector4d`
- `VectorBoxUtil`
- `VectorSphereUtil`

## com.hypixel.hytale.math.vector.relative

- `RelativeVector2d`
- `RelativeVector2i`
- `RelativeVector2l`
- `RelativeVector3d`
- `RelativeVector3i`
- `RelativeVector3l`

## com.hypixel.hytale.server.core

- `Message`

## com.hypixel.hytale.server.core.command.commands.debug

- `AssetTagsCommand`
- `AssetsCommand`
- `AssetsDuplicatesCommand`
- `DebugPlayerPositionCommand`
- `HitDetectionCommand`
- `HudManagerTestCommand`
- `LogCommand`
- `MessageTranslationTestCommand`
- `PIDCheckCommand`
- `PacketStatsCommand`
- `PingCommand`
- `ShowBuilderToolsHudCommand`
- `StopNetworkChunkSendingCommand`
- `TagPatternCommand`
- `VersionCommand`

## com.hypixel.hytale.server.core.command.commands.debug.component.hitboxcollision

- `HitboxCollisionAddCommand`
- `HitboxCollisionCommand`
- `HitboxCollisionRemoveCommand`

## com.hypixel.hytale.server.core.command.commands.debug.component.repulsion

- `RepulsionAddCommand`
- `RepulsionCommand`
- `RepulsionRemoveCommand`

## com.hypixel.hytale.server.core.command.commands.debug.packs

- `PacksCommand`
- `PacksListCommand`

## com.hypixel.hytale.server.core.command.commands.debug.server

- `ServerCommand`
- `ServerDumpCommand`
- `ServerGCCommand`
- `ServerStatsCommand`
- `ServerStatsCpuCommand`
- `ServerStatsGcCommand`
- `ServerStatsMemoryCommand`

## com.hypixel.hytale.server.core.command.commands.debug.stresstest

- `Bot`
- `BotConfig`
- `StressTestCommand`
- `StressTestStartCommand`
- `StressTestStopCommand`

## com.hypixel.hytale.server.core.command.commands.player

- `DamageCommand`
- `GameModeCommand`
- `HideCommand`
- `KillCommand`
- `PlayerCommand`
- `PlayerResetCommand`
- `PlayerRespawnCommand`
- `PlayerZoneCommand`
- `ReferCommand`
- `SudoCommand`
- `ToggleBlockPlacementOverrideCommand`
- `WhereAmICommand`
- `WhoAmICommand`

## com.hypixel.hytale.server.core.command.commands.player.camera

- `CameraDemo`
- `PlayerCameraDemoActivateCommand`
- `PlayerCameraDemoDeactivateCommand`
- `PlayerCameraDemoSubCommand`
- `PlayerCameraResetCommand`
- `PlayerCameraSideScrollerCommand`
- `PlayerCameraSubCommand`
- `PlayerCameraTopdownCommand`

## com.hypixel.hytale.server.core.command.commands.player.effect

- `PlayerEffectApplyCommand`
- `PlayerEffectClearCommand`
- `PlayerEffectSubCommand`

## com.hypixel.hytale.server.core.command.commands.player.inventory

- `GiveArmorCommand`
- `GiveCommand`
- `InventoryBackpackCommand`
- `InventoryClearCommand`
- `InventoryCommand`
- `InventoryItemCommand`
- `InventorySeeCommand`
- `ItemStateCommand`

## com.hypixel.hytale.server.core.command.commands.player.stats

- `PlayerStatsAddCommand`
- `PlayerStatsDumpCommand`
- `PlayerStatsGetCommand`
- `PlayerStatsResetCommand`
- `PlayerStatsSetCommand`
- `PlayerStatsSetToMaxCommand`
- `PlayerStatsSubCommand`

## com.hypixel.hytale.server.core.command.commands.player.viewradius

- `PlayerViewRadiusGetCommand`
- `PlayerViewRadiusSetCommand`
- `PlayerViewRadiusSubCommand`

## com.hypixel.hytale.server.core.command.commands.server

- `KickCommand`
- `MaxPlayersCommand`
- `StopCommand`
- `WhoCommand`

## com.hypixel.hytale.server.core.command.commands.server.auth

- `AuthCancelCommand`
- `AuthCommand`
- `AuthLoginBrowserCommand`
- `AuthLoginCommand`
- `AuthLoginDeviceCommand`
- `AuthLogoutCommand`
- `AuthPersistenceCommand`
- `AuthSelectCommand`
- `AuthStatusCommand`

## com.hypixel.hytale.server.core.command.commands.utility

- `BackupCommand`
- `ConvertPrefabsCommand`
- `EventTitleCommand`
- `NotifyCommand`
- `StashCommand`
- `UIGalleryCommand`
- `ValidateCPBCommand`

## com.hypixel.hytale.server.core.command.commands.utility.git

- `GitCommand`
- `UpdateAssetsCommand`
- `UpdatePrefabsCommand`

## com.hypixel.hytale.server.core.command.commands.utility.help

- `HelpCommand`

## com.hypixel.hytale.server.core.command.commands.utility.lighting

- `LightingCalculationCommand`
- `LightingCommand`
- `LightingGetCommand`
- `LightingInfoCommand`
- `LightingInvalidateCommand`
- `LightingSendCommand`
- `LightingSendToggleCommand`

## com.hypixel.hytale.server.core.command.commands.utility.metacommands

- `CommandsCommand`
- `DumpCommandsCommand`

## com.hypixel.hytale.server.core.command.commands.utility.net

- `NetworkCommand`

## com.hypixel.hytale.server.core.command.commands.utility.sleep

- `SleepCommand`
- `SleepOffsetCommand`
- `SleepTestCommand`

## com.hypixel.hytale.server.core.command.commands.utility.sound

- `SoundCommand`
- `SoundPlay2DCommand`
- `SoundPlay3DCommand`

## com.hypixel.hytale.server.core.command.commands.utility.worldmap

- `WorldMapClearMarkersCommand`
- `WorldMapCommand`
- `WorldMapDiscoverCommand`
- `WorldMapReloadCommand`
- `WorldMapUndiscoverCommand`
- `WorldMapViewRadiusGetCommand`
- `WorldMapViewRadiusRemoveCommand`
- `WorldMapViewRadiusSetCommand`
- `WorldMapViewRadiusSubCommand`

## com.hypixel.hytale.server.core.command.commands.world

- `SpawnBlockCommand`

## com.hypixel.hytale.server.core.command.commands.world.chunk

- `ChunkCommand`
- `ChunkFixHeightMapCommand`
- `ChunkForceTickCommand`
- `ChunkInfoCommand`
- `ChunkLightingCommand`
- `ChunkLoadCommand`
- `ChunkLoadedCommand`
- `ChunkMarkSaveCommand`
- `ChunkMaxSendRateCommand`
- `ChunkRegenerateCommand`
- `ChunkResendCommand`
- `ChunkTintCommand`
- `ChunkTrackerCommand`
- `ChunkUnloadCommand`

## com.hypixel.hytale.server.core.command.commands.world.entity

- `EntityCleanCommand`
- `EntityCloneCommand`
- `EntityCommand`
- `EntityCountCommand`
- `EntityDumpCommand`
- `EntityEffectCommand`
- `EntityHideFromAdventurePlayersCommand`
- `EntityIntangibleCommand`
- `EntityInvulnerableCommand`
- `EntityLodCommand`
- `EntityMakeInteractableCommand`
- `EntityNameplateCommand`
- `EntityRemoveCommand`
- `EntityResendCommand`
- `EntityTrackerCommand`

## com.hypixel.hytale.server.core.command.commands.world.entity.snapshot

- `EntitySnapshotHistoryCommand`
- `EntitySnapshotLengthCommand`
- `EntitySnapshotSubCommand`

## com.hypixel.hytale.server.core.command.commands.world.entity.stats

- `EntityStatsAddCommand`
- `EntityStatsDumpCommand`
- `EntityStatsGetCommand`
- `EntityStatsResetCommand`
- `EntityStatsSetCommand`
- `EntityStatsSetToMaxCommand`
- `EntityStatsSubCommand`

## com.hypixel.hytale.server.core.command.commands.world.worldgen

- `WorldGenBenchmarkCommand`
- `WorldGenCommand`
- `WorldGenReloadCommand`

## com.hypixel.hytale.server.core.command.system

- `AbbreviationMap`
- `AbstractCommand`
- `CommandContext`
- `CommandManager`
- `CommandOwner`
- `CommandRegistration`
- `CommandRegistry`
- `CommandSender`
- `CommandUtil`
- `CommandValidationResults`
- `MatchResult`
- `ParseResult`
- `ParserContext`
- `Tokenizer`

## com.hypixel.hytale.server.core.command.system.arguments.system

- `AbstractOptionalArg`
- `ArgWrapper`
- `Argument`
- `DefaultArg`
- `FlagArg`
- `OptionalArg`
- `RequiredArg`
- `WrappedArg`

## com.hypixel.hytale.server.core.command.system.arguments.types

- `AbstractAssetArgumentType`
- `ArgTypes`
- `ArgumentType`
- `AssetArgumentType`
- `BooleanFlagArgumentType`
- `Coord`
- `EntityWrappedArg`
- `EnumArgumentType`
- `GameModeArgumentType`
- `IntCoord`
- `ListArgumentType`
- `MultiArgumentContext`
- `MultiArgumentType`
- `ProcessedArgumentType`
- `RelativeChunkPosition`
- `RelativeDirection`
- `RelativeDoublePosition`
- `RelativeFloat`
- `RelativeIntPosition`
- `RelativeInteger`
- `RelativeIntegerRange`
- `RelativeVector3i`
- `SingleArgumentType`
- `WrappedArgumentType`

## com.hypixel.hytale.server.core.command.system.basecommands

- `AbstractAsyncCommand`
- `AbstractAsyncPlayerCommand`
- `AbstractAsyncWorldCommand`
- `AbstractCommandCollection`
- `AbstractPlayerCommand`
- `AbstractTargetEntityCommand`
- `AbstractTargetPlayerCommand`
- `AbstractWorldCommand`
- `CommandBase`

## com.hypixel.hytale.server.core.command.system.exceptions

- `CommandException`
- `GeneralCommandException`
- `NoPermissionException`
- `SenderTypeException`

## com.hypixel.hytale.server.core.command.system.pages

- `CommandListPage`
- `UIGalleryPage`

## com.hypixel.hytale.server.core.command.system.suggestion

- `SuggestionProvider`
- `SuggestionResult`

## com.hypixel.hytale.server.core.plugin

- `JavaPlugin`
- `JavaPluginInit`
- `MissingPluginDependencyException`
- `PluginBase`
- `PluginClassLoader`
- `PluginInit`
- `PluginListPageManager`
- `PluginManager`
- `PluginState`
- `PluginType`

## com.hypixel.hytale.server.core.plugin.commands

- `PluginCommand`

## com.hypixel.hytale.server.core.plugin.event

- `PluginEvent`
- `PluginSetupEvent`

## com.hypixel.hytale.server.core.plugin.pages

- `PluginListPage`

## com.hypixel.hytale.server.core.plugin.pending

- `PendingLoadJavaPlugin`
- `PendingLoadPlugin`

## com.hypixel.hytale.server.core.plugin.registry

- `AssetRegistry`
- `CodecMapRegistry`
- `IRegistry`
- `MapKeyMapRegistry`

## com.hypixel.hytale.server.core.universe

- `PlayerRef`
- `Universe`
- `WorldLoadCancelledException`

## com.hypixel.hytale.server.core.universe.datastore

- `DataStore`
- `DataStoreProvider`
- `DiskDataStore`
- `DiskDataStoreProvider`

## com.hypixel.hytale.server.core.universe.playerdata

- `DefaultPlayerStorageProvider`
- `DiskPlayerStorageProvider`
- `PlayerStorage`
- `PlayerStorageProvider`

## com.hypixel.hytale.server.core.universe.system

- `PlayerRefAddedSystem`
- `PlayerVelocityInstructionSystem`
- `WorldConfigSaveSystem`

## com.hypixel.hytale.server.core.universe.world

- `ClientEffectWorldSettings`
- `IWorldChunks`
- `IWorldChunksAsync`
- `ParticleUtil`
- `PlaceBlockSettings`
- `PlayerUtil`
- `SetBlockSettings`
- `SoundUtil`
- `SpawnUtil`
- `ValidationOption`
- `World`
- `WorldConfig`
- `WorldConfigProvider`
- `WorldMapTracker`
- `WorldNotificationHandler`
- `WorldProvider`

## com.hypixel.hytale.server.core.universe.world.accessor

- `BlockAccessor`
- `ChunkAccessor`
- `EmptyBlockAccessor`
- `IChunkAccessorSync`
- `LocalCachedChunkAccessor`
- `OverridableChunkAccessor`

## com.hypixel.hytale.server.core.universe.world.chunk

- `AbstractCachedAccessor`
- `BlockChunk`
- `BlockComponentChunk`
- `BlockRotationUtil`
- `ChunkColumn`
- `ChunkFlag`
- `EntityChunk`
- `WorldChunk`

## com.hypixel.hytale.server.core.universe.world.chunk.environment

- `EnvironmentChunk`
- `EnvironmentColumn`
- `EnvironmentRange`

## com.hypixel.hytale.server.core.universe.world.chunk.palette

- `BitFieldArr`
- `IntBytePalette`
- `ShortBytePalette`

## com.hypixel.hytale.server.core.universe.world.chunk.section

- `BlockSection`
- `ChunkLightData`
- `ChunkLightDataBuilder`
- `ChunkSection`
- `ChunkSectionReference`
- `FluidSection`

## com.hypixel.hytale.server.core.universe.world.chunk.section.blockpositions

- `BlockPositionData`
- `BlockPositionProvider`
- `IBlockPositionData`

## com.hypixel.hytale.server.core.universe.world.chunk.section.palette

- `AbstractByteSectionPalette`
- `AbstractShortSectionPalette`
- `ByteSectionPalette`
- `EmptySectionPalette`
- `HalfByteSectionPalette`
- `ISectionPalette`
- `PaletteTypeEnum`
- `ShortSectionPalette`

## com.hypixel.hytale.server.core.universe.world.chunk.state

- `TickableBlockState`

## com.hypixel.hytale.server.core.universe.world.chunk.systems

- `ChunkSystems`

## com.hypixel.hytale.server.core.universe.world.commands

- `SetTickingCommand`
- `WorldSettingsCommand`

## com.hypixel.hytale.server.core.universe.world.commands.block

- `BlockCommand`
- `BlockGetCommand`
- `BlockGetStateCommand`
- `BlockInspectFillerCommand`
- `BlockInspectPhysicsCommand`
- `BlockInspectRotationCommand`
- `BlockRowCommand`
- `BlockSelectCommand`
- `BlockSetCommand`
- `BlockSetStateCommand`
- `BlockSetTickingCommand`
- `SimpleBlockCommand`

## com.hypixel.hytale.server.core.universe.world.commands.block.bulk

- `BlockBulkCommand`
- `BlockBulkFindCommand`
- `BlockBulkFindHereCommand`
- `BlockBulkReplaceCommand`

## com.hypixel.hytale.server.core.universe.world.commands.world

- `WorldAddCommand`
- `WorldCommand`
- `WorldListCommand`
- `WorldLoadCommand`
- `WorldPruneCommand`
- `WorldRemoveCommand`
- `WorldRocksDbCommand`
- `WorldSaveCommand`
- `WorldSetDefaultCommand`

## com.hypixel.hytale.server.core.universe.world.commands.world.perf

- `WorldPerfCommand`
- `WorldPerfGraphCommand`
- `WorldPerfResetCommand`

## com.hypixel.hytale.server.core.universe.world.commands.world.tps

- `WorldTpsCommand`
- `WorldTpsResetCommand`

## com.hypixel.hytale.server.core.universe.world.commands.worldconfig

- `WorldConfigCommand`
- `WorldConfigPauseTimeCommand`
- `WorldConfigSeedCommand`
- `WorldConfigSetPvpCommand`
- `WorldConfigSetSpawnCommand`
- `WorldConfigSetSpawnDefaultCommand`
- `WorldPauseCommand`

## com.hypixel.hytale.server.core.universe.world.connectedblocks

- `ConnectedBlockFaceTags`
- `ConnectedBlockPatternRule`
- `ConnectedBlockRuleSet`
- `ConnectedBlockShape`
- `ConnectedBlocksModule`
- `ConnectedBlocksUtil`
- `CustomConnectedBlockPattern`
- `CustomConnectedBlockTemplateAsset`
- `CustomTemplateConnectedBlockPattern`
- `CustomTemplateConnectedBlockRuleSet`
- `PatternRotationDefinition`
- `Rotation3D`

## com.hypixel.hytale.server.core.universe.world.connectedblocks.builtin

- `ConnectedBlockOutput`
- `RoofConnectedBlockRuleSet`
- `StairConnectedBlockRuleSet`
- `StairLikeConnectedBlockRuleSet`

## com.hypixel.hytale.server.core.universe.world.events

- `AddWorldEvent`
- `AllWorldsLoadedEvent`
- `ChunkEvent`
- `ChunkPreLoadProcessEvent`
- `RemoveWorldEvent`
- `StartWorldEvent`
- `WorldEvent`

## com.hypixel.hytale.server.core.universe.world.events.ecs

- `ChunkSaveEvent`
- `ChunkUnloadEvent`
- `MoonPhaseChangeEvent`

## com.hypixel.hytale.server.core.universe.world.lighting

- `CalculationResult`
- `ChunkLightingManager`
- `FloodLightCalculation`
- `FullBrightLightCalculation`
- `LightCalculation`

## com.hypixel.hytale.server.core.universe.world.map

- `WorldMap`

## com.hypixel.hytale.server.core.universe.world.meta

- `BlockState`
- `BlockStateModule`
- `BlockStateRegistration`
- `BlockStateRegistry`

## com.hypixel.hytale.server.core.universe.world.meta.state

- `BlockMapMarker`
- `BlockMapMarkersResource`
- `BreakValidatedBlockState`
- `DestroyableBlockState`
- `ItemContainerBlockState`
- `ItemContainerState`
- `LaunchPad`
- `PlacedByBlockState`
- `RespawnBlock`
- `SendableBlockState`

## com.hypixel.hytale.server.core.universe.world.meta.state.exceptions

- `NoSuchBlockStateException`

## com.hypixel.hytale.server.core.universe.world.npc

- `INonPlayerCharacter`

## com.hypixel.hytale.server.core.universe.world.path

- `IPath`
- `IPathWaypoint`
- `SimplePathWaypoint`
- `WorldPath`
- `WorldPathChangedEvent`
- `WorldPathConfig`

## com.hypixel.hytale.server.core.universe.world.spawn

- `FitToHeightMapSpawnProvider`
- `GlobalSpawnProvider`
- `ISpawnProvider`
- `IndividualSpawnProvider`

## com.hypixel.hytale.server.core.universe.world.storage

- `BufferChunkLoader`
- `BufferChunkSaver`
- `ChunkStore`
- `EntityStore`
- `GetChunkFlags`
- `IChunkLoader`
- `IChunkSaver`

## com.hypixel.hytale.server.core.universe.world.storage.component

- `ChunkSavingSystems`
- `ChunkUnloadingSystem`

## com.hypixel.hytale.server.core.universe.world.storage.provider

- `DefaultChunkStorageProvider`
- `EmptyChunkStorageProvider`
- `IChunkStorageProvider`
- `IndexedStorageChunkStorageProvider`
- `MigrationChunkStorageProvider`
- `RocksDbChunkStorageProvider`

## com.hypixel.hytale.server.core.universe.world.storage.resources

- `DefaultResourceStorageProvider`
- `DiskResourceStorageProvider`
- `EmptyResourceStorageProvider`
- `IResourceStorageProvider`

## com.hypixel.hytale.server.core.universe.world.system

- `WorldPregenerateSystem`

## com.hypixel.hytale.server.core.universe.world.worldgen

- `GeneratedBlockChunk`
- `GeneratedBlockStateChunk`
- `GeneratedChunk`
- `GeneratedChunkSection`
- `GeneratedEntityChunk`
- `IBenchmarkableWorldGen`
- `IWorldGen`
- `IWorldGenBenchmark`
- `ValidatableWorldGen`
- `WorldGenLoadException`
- `WorldGenTimingsCollector`

## com.hypixel.hytale.server.core.universe.world.worldgen.provider

- `DummyWorldGenProvider`
- `FlatWorldGenProvider`
- `IWorldGenProvider`
- `VoidWorldGenProvider`

## com.hypixel.hytale.server.core.universe.world.worldlocationcondition

- `WorldLocationCondition`

## com.hypixel.hytale.server.core.universe.world.worldmap

- `IWorldMap`
- `WorldMapLoadException`
- `WorldMapManager`
- `WorldMapSettings`

## com.hypixel.hytale.server.core.universe.world.worldmap.markers

- `MapMarkerBuilder`
- `MapMarkerTracker`
- `MarkersCollector`
- `MarkersCollectorImpl`

## com.hypixel.hytale.server.core.universe.world.worldmap.markers.providers

- `DeathMarkerProvider`
- `OtherPlayersMarkerProvider`
- `POIMarkerProvider`
- `PersonalMarkersProvider`
- `RespawnMarkerProvider`
- `SharedMarkersProvider`
- `SpawnMarkerProvider`

## com.hypixel.hytale.server.core.universe.world.worldmap.markers.user

- `UserMapMarker`
- `UserMapMarkersStore`
- `UserMarkerValidator`

## com.hypixel.hytale.server.core.universe.world.worldmap.markers.utils

- `MapMarkerUtils`

## com.hypixel.hytale.server.core.universe.world.worldmap.markers.worldstore

- `WorldMarkersResource`

## com.hypixel.hytale.server.core.universe.world.worldmap.provider

- `DisabledWorldMapProvider`
- `IWorldMapProvider`

## com.hypixel.hytale.server.core.universe.world.worldmap.provider.chunk

- `ChunkWorldMap`
- `ImageBuilder`
- `WorldGenWorldMapProvider`

---

## Key Class Signatures

Use `javap -cp <jar> <classname>` to get method signatures.

Example:
```
javap -cp "C:\Users\alexispace\.gradle\caches\modules-2\files-2.1\com.hypixel.hytale\Server\2026.02.18-f3b8fff95\c53b345d2d97cfb80e4e9fdca72f3935d8ab5cd1\Server-2026.02.18-f3b8fff95.jar" com.hypixel.hytale.server.core.command.system.CommandContext
```
