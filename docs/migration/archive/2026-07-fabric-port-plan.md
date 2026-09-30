# 历史存档：2026 年 7 月 Fabric 移植计划

> 本文件保留整理前的移植记录，仅供历史参考，不再更新。
> 其中的依赖版本、API 映射、进度及未完成事项可能已过时，不应直接用作当前实现指南。
> 当前决策和待办以根目录 [FABRIC_PORT_PLAN.md](../../../FABRIC_PORT_PLAN.md) 为准。

## 原始记录

# ProjectE NeoForge → Fabric 1.21.1 移植计划

> 状态文档:记录移植决策、阶段进度与遗留事项。每完成一个阶段更新对应章节的状态标记。
> 目标:将 ProjectE(当前基于 NeoForge 21.1.148 / MC 1.21.1)完整移植为 Fabric mod。

## 一、现状盘点(2026-07-19 调查结论)

- 代码规模:480 个 Java 文件(api 69 / main 373 / datagen 28 / test 10),221 个文件直接 import `net.neoforged.*`
- 映射:Mojang 官方映射 + Parchment(2024.11.17)——Loom 同样支持,**原版类引用全部保留不动**
- 注册:自研包装层 `gameObjs/registration`(PEDeferredRegister/PEDeferredHolder 等 24 个类)包住了 NeoForge DeferredRegister,15 个注册表类调用点无需大改
- 网络:vanilla `CustomPacketPayload` + `StreamCodec`,由 `network/PacketHandler` 统一注册,约 20 个包
- 能力:8 个物品能力接口(IItemEmcHolder/IItemCharge/IPedestalItem/IModeChanger/IAlchBagItem/IAlchChestItem/IExtraFunction/IProjectileShooter)+ 玩家实体能力(Knowledge/AlchBag)+ 方块 IItemHandler 暴露;54 个文件使用 IItemHandler/ItemStackHandler
- 附件:3 个玩家 Attachment(alchemical_bags / knowledge / gem_armor_state),均 copyOnDeath + Codec 序列化
- 配置:NeoForge `ModConfigSpec`(client/common/server 三个 TOML),全 mod 大量调用缓存配置值
- 资源:`src/datagen/generated` 已入库 792 个生成文件(配方/模型/战利品表/标签),1.21.1 两加载器通用 `c:` 标签
- 事件:12 处 @SubscribeEvent + PECore 约 20 处 addListener + API 事件 4 个(NeoForge Event 子类)
- 集成:JEI、EMI、Curios、Jade、WTHIT、TOP、CraftTweaker

## 二、已确认决策(用户拍板)

| 决策点 | 结论 |
|---|---|
| 加载器 | Fabric(单加载器,原地替换,不做 Architectury 双端) |
| 映射 | Mojmap + Parchment(Loom layered mappings) |
| 配置系统 | **Forge Config API Port**(Fuzs),ModConfigSpec API 原样保留,JiJ 内嵌 |
| 配方查看器 | **JEI + EMI + REI 三家都要**(JEI/EMI 移植现有插件,REI 基于共用抽象层新写原生插件) |
| 方块信息 | Jade + WTHIT(均有 Fabric 版) |
| 饰品栏 | Curios 集成重写为 **Trinkets** |
| 放弃 | TOP(无 Fabric 1.21.1 版)、CraftTweaker(工作量大,遗留后续) |
| datagen/test | 暂时移出编译,复用已生成资源;后续再移植到 Fabric Datagen |

## 三、技术映射表

| NeoForge | Fabric 方案 |
|---|---|
| `@Mod` + IEventBus 构造注入 | `ModInitializer` / `ClientModInitializer` entrypoint |
| DeferredRegister(包装层内部) | 直接 `Registry.register`(保持包装层对外 API 不变) |
| `NeoForgeRegistries.Keys.ATTACHMENT_TYPES` | Fabric Data Attachment API(`AttachmentRegistry`,persistent + copyOnDeath) |
| NewRegistryEvent 自定义注册表(NSS_SERIALIZER) | `FabricRegistryBuilder` |
| ItemCapability / EntityCapability / 方块能力 | Fabric `ItemApiLookup` / `EntityApiLookup` / `BlockApiLookup`(fabric-api-lookup-api-v1) |
| `IItemHandler`/`ItemStackHandler`(neoforge.items) | **API 源集清洁室自建**同形接口(`moze_intel.projecte.api.item_handlers`),方块实体桥接 `WorldlyContainer` 保证漏斗/管道互通(Fabric ItemStorage.SIDED 对 Container 有自动回退) |
| PayloadRegistrar / PacketDistributor | `PayloadTypeRegistry.playC2S/playS2C` + `ServerPlayNetworking`/`ClientPlayNetworking`,自建 `PEPacketContext` 让各包改动最小 |
| ModConfigSpec + ModConfigEvent | Forge Config API Port v4(`NeoForgeConfigRegistry` + `NeoForgeModConfigEvents`),配置类零改动 |
| NeoForge 事件总线(游戏事件) | Fabric 回调:ServerLifecycleEvents / ServerTickEvents / CommandRegistrationCallback / ResourceManagerHelper(SERVER_DATA reload)/ ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS(datapack sync)等 |
| 无 Fabric 等价事件(ItemAttributeModifierEvent、部分玩家事件) | Mixin(ItemStack 属性收集、攻击/受伤钩子按需) |
| PE API 事件(NeoForge Event 子类) | Fabric `Event<T>`(EventFactory.createArrayBacked) |
| `ItemAbilities` / `getToolModifiedState` | 自研 ability 分发 + AW 打开原版映射表(AxeItem.STRIPPABLES / ShovelItem.FLATTENABLES / HoeItem.TILLABLES);外部检查改内部谓词 |
| `state.isFlammable` / `onCaughtFire` | `FlammableBlockRegistry` + TNT 特判 |
| FluidStack / IFluidHandler | Fabric Transfer API(FluidVariant / Storage<FluidVariant>),仅潮汐/火山护符与配方查看器展示用到 |
| NeoForge PermissionAPI(PEPermissions) | fabric-permissions-api(me.lucko,JiJ),默认回退 OP 等级 |
| NeoForge 条件配方(PERecipeConditions) | Fabric Resource Conditions(v1),生成 JSON 中 `neoforge:conditions` → `fabric:load_conditions` |
| NeoForge data maps(炉燃料/堆肥) | 运行时注册(`FuelRegistry` / `CompostingChanceRegistry`) |
| accesstransformer.cfg | `projecte.accesswidener` |
| neoforge.mods.toml | `fabric.mod.json`(entrypoints/depends/AW/mixins/icon) |
| commons-math3 shadow 重定位 | Loom `include`(JiJ,Fabric 嵌套 jar 机制无需重定位) |
| NeoForge testframework 单测 | 暂时禁用 |

## 四、阶段划分(对应任务 #1–#7)

1. **构建系统** — Loom 插件、mojmap+parchment、fabric.mod.json、AW 转换、依赖坐标(全部用 context7/tavily 核实最新版本)、datagen/test 移出编译。验收:`gradlew tasks` 配置通过、依赖解析成功。
2. **核心兼容层** — item handler 自建 API、能力 → ApiLookup、注册包装层内部重写、附件、API 事件、PacketHandler、配置注册、PECore → ModInitializer。
3. **main 源集批量移植** — 按包推进:gameObjs → emc → impl → handlers → utils → world_transmutation → events(含 Mixin)。
4. **客户端** — ClientModInitializer:屏幕/渲染器/模型层/按键/物品属性/tooltip/玩家渲染。
5. **资源修正** — conditions 转换、data maps 运行时化、NeoForge 专属资源清理。
6. **集成** — JEI/EMI 移植 + REI 原生新写 + Jade/WTHIT 适配 + Curios→Trinkets 重写;全部 isModLoaded 门控。
7. **编译修复与验证** — build 通过、jar 结构检查、runServer 冒烟、遗留清单归档。

## 五、风险与注意事项

- **API 破坏性变更**:PE 公开 API 从 NeoForge 类型改为 Fabric/自建类型,现有 NeoForge 附属 mod 不兼容(不可避免,需在 README/API 文档注明)
- **漏斗/管道互通**:方块实体必须实现 `WorldlyContainer`(Fabric 生态依赖 Transfer API 的自动回退),侧面访问规则要与原 IItemHandler 暴露逻辑一致
- **Fabric Attachment copyOnDeath 语义**:与 NeoForge copyHandler 有差异(实现阶段核对 Fabric API 文档,必要时手动在 respawn 事件复制)
- **ItemAbilities 外部互操作丧失**:其他 mod 无法再通过 NeoForge ability 体系识别 PE 工具(Fabric 无对应标准,用 `c:` 工具标签尽量补偿)
- **版本坐标**:所有依赖版本必须实时查证(context7/tavily),禁止凭记忆写版本号
- **本地构建环境**:GRADLE_USER_HOME 固定在项目内 `.gradle-home/`(gradlew 脚本注入);Gradle 发行包用腾讯云镜像下载
- Windows 环境,PowerShell 为主;gradle 命令用 `.\gradlew`

## 六、进度跟踪

- [x] 调查与决策(本文档)
- [x] 阶段1:构建系统
- [x] 阶段2:核心兼容层
- [x] 阶段3:main 移植
- [x] 阶段4:客户端
- [x] 阶段5:资源修正
- [x] 阶段6:集成(JEI/EMI/REI/WTHIT/Jade 已适配;Curios→Trinkets 已重写)
- [ ] 阶段7:运行验证(runServer 冒烟测试)

## 七、遗留事项(移植完成后)

### 已知行为差异/缺口(移植中产生,待后续补齐)
- ~~**方块实体漏斗互通缺失**:~~✅ 已修复(2026-07-20):PEWorldlyContainer 接口已实现,7 个带 INVENTORY_PROVIDER 的 BE 均已适配
- ~~**Arcana/疾风戒指创造飞行冲突**:~~✅ 已修复(2026-07-20):由 InternalAbilities 统一管理 mayfly,避免 Arcana 在同一 tick 撤销疾风戒指的飞行权限
- ~~**贤者之石世界转换表为空**:~~✅ 已修复(2026-07-20):WorldTransmutationManager 已注册为 Fabric SERVER_DATA reload listener
- ~~**物质熔炉拒绝 ProjectE 燃料**:~~✅ 已修复(2026-07-20):三类燃料及方块形态运行时注册到 Fabric FuelRegistry,熔炉槽位只接受 ProjectE 燃料或 EMC Holder
- ~~**物质工具 3x 模式不生效**:~~✅ 已修复(2026-07-20):移除原方块删除后的坐标二次命中要求,保留命中面推导用于范围挖掘
- **卡塔尔剪切不再翻倍掉落**:vanilla Shearable.shear() 直接生成物品实体,无法获取掉落列表翻倍;后续需用 Mixin 拦截或重新实现剪切逻辑
- **锄头 AOE 耕地仅支持原版转换表**(硬编码 [VanillaCopy]),模组自定义耕地块不参与 AOE。
- **可取消事件钩子移除**:水弹造石、VoidRing 传送、新星爆炸不再可被其他 mod 事件拦截。
- **GemLegs 跳跃检测**改为速度启发式(原 LivingJumpEvent)。
- **canPerformAction/ItemAbility 体系删除**:外部 mod 无法通过该体系识别 PE 工具。
- **JEI 切屏标记(switchingToJEI)暂缺**:Phase 6 恢复。
- ~~**REI 未适配**~~:✅ 已适配(2026-07-22):基于共用抽象层 `integration/recipe_viewer/rei` 新写原生 `REIClientPlugin`(收集器 + 世界转换两个分类 / 显示 + 工作站),fabric.mod.json 加 `rei_client` 入口点。**留白**:物品子类型比较(模式 / 存储 EMC 区分为不同条目,EMI/JEI 已有)未移植,REI 的 `ItemComparatorRegistry` 走不同 API,后续可补。
- ~~**Curios 未适配**~~:✅ 重写为 Trinkets(2026-07-22):`IntegrationHelper` 三方法经 `isModLoaded` 门控委派到 `integration/trinkets`,13 个饰品经 `registerCuriosCapability`→`TrinketsApi.registerTrinket` 注册,`getCuriosInventory` 返回覆盖佩戴库存的 `IItemHandler`(活引用,支持原地改),`IExposesCurioAttributes`(Arcana)属性经 `Trinket.getModifiers` 生效;数据包 `data/trinkets/entities/projecte.json`(玩家授 hand/ring + chest/necklace 槽)+ 两个槽位 item tag。**留白**:佩戴戒指的主动 tick 效果仍受各物品 `inventoryTick` 的 `hotBarOrOffHand` 门控(沿用物品既有设计,佩戴态不触发);佩戴饰品的玩家模型渲染(`TrinketRenderer`)未做(纯外观)。

### 原有遗留
- datagen 源集移植到 Fabric Data Generation API
- test 源集脱离 NeoForge testframework
- CraftTweaker Fabric 集成评估
- README 更新(Fabric 版说明、API 变更公告)
