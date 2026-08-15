# GoldMiner 挖矿小游戏插件

适用于 Purpur 1.21.11 的挖矿小游戏插件，玩家在独立矿场世界中挖矿获取金币与经验，升级镐子、购买道具、组建小队。

---

## 目录

- [依赖插件](#依赖插件)
- [安装与启用](#安装与启用)
- [命令参考](#命令参考)
  - [玩家命令](#玩家命令)
  - [管理员命令](#管理员命令)
- [PlaceholderAPI 占位符](#placeholderapi-占位符)
- [配置文件](#配置文件)
  - [config.yml](#configyml)
  - [lang.yml](#langyml)
  - [block.yml](#blockyml)
  - [shop.yml](#shopyml)
- [功能详解](#功能详解)
  - [矿场系统](#矿场系统)
  - [镐子升级](#镐子升级)
  - [暴击系统](#暴击系统)
  - [药水商店](#药水商店)
  - [暴击率/暴击倍率商店](#暴击率暴击倍率商店)
  - [等级购买](#等级购买)
  - [连锁体验卡](#连锁体验卡)
  - [一般连锁](#一般连锁)
  - [小队系统](#小队系统)
  - [货币兑换](#货币兑换)
- [权限节点](#权限节点)
- [常见问题](#常见问题)

---

## 依赖插件

| 插件 | 必需 | 说明 |
|------|------|------|
| **Multiverse-Core** | ✅ 是 | 世界管理 |
| **Vault** | ✅ 是 | 经济系统对接 |
| **PlaceholderAPI** | 否 | 占位符支持（推荐安装） |

---

## 安装与启用

1. 确保已安装依赖插件
2. 将 `GoldMiner.jar` 放入服务器的 `plugins/` 目录
3. 重启服务器或执行 `/plugman load GoldMiner`
4. 插件会在 `plugins/GoldMiner/` 生成配置文件：
   - `config.yml` - 主配置
   - `lang.yml` - 语言/消息
   - `block.yml` - 矿物定义
   - `shop.yml` - 商店定价
5. 根据需要修改配置文件，执行 `/goldminer reload` 重载

---

## 命令参考

### 玩家命令

| 命令 | 说明 |
|------|------|
| `/goldminer join` | 加入矿场世界，开始挖矿 |
| `/goldminer shop` | 打开矿场商店 GUI |
| `/goldminer info` | 查看矿工信息（等级/金币/暴击率等） |
| `/goldminer suit` | 切换装备显示/隐藏 |
| `/goldminer buy lv <数量>` | 精确购买指定等级 |
| `/goldminer buy lv <数量> confirm` | 确认执行等级购买 |
| `/goldminer team create` | 创建小队 |
| `/goldminer team join <队名>` | 申请加入小队 |
| `/goldminer team accept [玩家名]` | 接受入队申请 |
| `/goldminer team leave` | 退出小队（经验/等级清空） |
| `/goldminer team list` | 查看小队列表 |
| `/goldminer top` | 查看排行榜 |
| `/goldminer exchange [数量]` | 兑换矿场金币为主世界货币 |
| `/goldminer help` | 查看帮助 |

### 管理员命令

| 命令 | 说明 |
|------|------|
| `/goldminer reload` | 重载所有配置文件 |
| `/goldminer reload pool` | 强制刷新矿池（矿物重新随机） |
| `/goldminer reload info` | 刷新玩家数据与矿场世界信息 |
| `/goldminer shop set <key> <价格>` | 热修改商店价格（即时生效） |
| `/goldminer set exp\|lv <玩家> <数量>` | 设置玩家经验/等级 |
| `/goldminer add exp\|lv <玩家> <数量>` | 添加玩家经验/等级 |
| `/goldminer remove exp\|lv <玩家> <数量>` | 移除玩家经验/等级 |

---

## PlaceholderAPI 占位符

| 占位符 | 返回值 | 说明 |
|--------|--------|------|
| `%goldminer_reload_time%` | 整数 | 矿场刷新间隔（秒） |
| `%goldminer_user_level%` | 整数 | 玩家当前等级 |
| `%goldminer_user_money%` | 整数 | 玩家金币数 |
| `%goldminer_crit_hit_rate%` | 百分比字符串 | 暴击率（如 "5.0"） |
| `%goldminer_crit_magnification%` | 倍率字符串 | 暴击倍率（如 "2.5"） |
| `%goldminer_crit_time%` | 整数 | 额外暴击倍率剩余时间（秒） |
| `%goldminer_interlocking_type%` | 字符串 | 当前连锁类型（"无"/平面X/平面Z/半径/视角方向） |
| `%goldminer_interlocking_time%` | 整数 | 连锁体验卡剩余时间（秒） |
| `%goldminer_nomal_interlocking_time%` | 整数 | 一般连锁剩余时间（秒） |

---

## 配置文件

### config.yml

主配置文件，控制矿场参数、镐子附魔上限、药水效果、暴击系统等。

```yaml
# 存储设置
storage:
  type: sqlite              # sqlite 或 mysql
  mysql:
    host: localhost
    port: 3306
    database: goldminer
    username: root
    password: password

# 矿场设置
mine:
  world-name: "goldminer_mine"  # 矿场世界名称
  border-size: 2000             # 世界边界
  center-size: 100              # 矿场核心区域边长
  refresh-interval: 30          # 自动刷新间隔（秒）

# 暴击系统
crit-system:
  default-crit-rate: 0.005         # 初始暴击率（0.5%）
  default-crit-magnification: 0.5  # 初始暴击倍率
  bonus-crit-mag-duration: 1800    # 额外暴击倍率持续时间（秒）
  overflow-exp-multiplier: 500.0   # 溢出暴击率转换经验倍率

# 垫脚玻璃
glass-block:
  material: GLASS          # 玻璃方块类型
  name: "&f垫脚玻璃 &7(无限使用)"
  lore: "&7可无限放置的玻璃方块"

# 镐子附魔上限（按镐子等级）
pickaxe-enchant-limits:
  default: {efficiency: 5, fortune: 3, unbreaking: 3}
  iron: {efficiency: 10, ...}
  diamond: {efficiency: 30, fortune: 10, ...}
  netherite: {efficiency: 255, fortune: 15, ...}
```

### lang.yml

语言/消息文件，所有显示文本均可在此配置：

```yaml
# 挖掘消息
mining:
  coin-earned: "&6+{coin} 金币 &7| &a+{exp} 经验"
  level-up: "&a恭喜！你的矿工等级提升到了 &e{level} &a级！"
  pickaxe-upgrade: "&a你的镐子已升级为 &e{pickaxe}&a！"
  enchant-upgrade: "&a你的镐子附魔已提升！"

# GUI按钮
gui:
  button:
    return-spawn: {name: "&a返回主城", ...}
    create-team: {name: "&b创建小队", ...}
```

### block.yml

矿物定义文件，可添加/修改矿物及奖励：

```yaml
common:
  STONE:        {probability: 15.0, coin: 1, exp: 1}
  COBBLESTONE:  {probability: 15.0, coin: 1, exp: 1}
  ...

rare:
  IRON_ORE:     {probability: 10.0, coin: 10, exp: 5}
  COPPER_ORE:   {probability: 8.0,  coin: 8,  exp: 4}
  ...

epic:
  GOLD_ORE:     {probability: 5.0, coin: 30, exp: 15}
  ...

legendary:
  DIAMOND_ORE:  {probability: 3.0, coin: 100, exp: 50}
  ...
```

- `probability`：该品质内部的相对概率
- `coin`：挖掘奖励金币
- `exp`：挖掘奖励经验

### shop.yml

商店定价文件，所有价格与消息均可配置：

```yaml
# 药水商店
potion:
  available-durations: [30, 60, 300, 600, 1800, 3600]  # 可选时长（秒）
  max-level: 30
  haste:
    base-price: 30
    level-multiplier: 0.5
    duration-multiplier: 0.3

# 暴击率商店
crit-rate:
  base-price: 300
  tiers:
    0.5pct: 1.0
    1pct: 3.0
    ...

# 连锁体验卡
# 价格公式: 基础价格 × 阶梯倍率^(范围-1)
chain-card:
  duration-seconds: 30
  plane_x: {base-price: 8000, tier-multiplier: 3.0}
  plane_z: {base-price: 8000, tier-multiplier: 3.0}
  radius:  {base-price: 15000, tier-multiplier: 3.5}
  ray:     {base-price: 6000,  tier-multiplier: 2.5}

# 一般连锁
global-chain:
  price: 100
  duration-seconds: 10800    # 3小时

# 商店图标
shop-icons:
  chain-card-plane-x: OAK_PLANKS
  chain-card-plane-z: OAK_PLANKS
  chain-card-radius: STONE
  chain-card-ray: ARROW
```

---

## 功能详解

### 矿场系统

- 独立的共享矿场世界（`goldminer_mine`）
- 矿场为 100×100×100 立方体，矿物按品质概率随机生成
- 品质分布（config.yml 可调）：普通 85%、稀有 8%、史诗 6%、传奇 1%
- 自动定时刷新（默认 30 秒），刷新时所有方块重新随机
- 玩家在矿场顶部安全平台出生，装备自动保护

**进入与退出**：
- 执行 `/goldminer join` → 传送到矿场 → 获得木镐 + 菜单星 + 无限垫脚玻璃
- 退出矿场世界（传送回主世界）→ 自动清除矿场追踪 → 可重新 join

### 镐子升级

玩家挖矿获得经验 → 逐级提升等级 → 自动升级镐子：

| 镐子 | 升级条件 | 附魔上限 |
|------|----------|----------|
| 木镐 | 初始 | 效率5 / 时运3 / 耐久3 |
| 石镐 | Lv.2 | 效率5 / 时运3 / 耐久3 |
| 铜镐 | Lv.22 | 效率5 / 时运3 / 耐久3 |
| 金镐 | Lv.42 | 效率5 / 时运3 / 耐久3 |
| 铁镐 | Lv.72 | 效率10 / 时运3 / 耐久3 |
| 钻石镐 | Lv.102 | 效率30 / 时运10 / 耐久5 |
| 下界合金镐 | Lv.132 | 效率255 / 时运15 / 耐久10 |

- 每级附魔逐级提升，满后晋升下一镐子等级
- 晋升时随机继承一个满级附魔
- 装备随镐子等级自动更换（可 `/goldminer suit` 切换显隐）

### 暴击系统

- **暴击率**：挖矿时独立判定，初始 0.5%，可购买提升至 100%
- **暴击倍率**：触发暴击时额外获得的倍率，初始 0.5x
- 暴击效果：`原始金币 + 原始金币 × 暴击倍率`（四舍五入）
- 连锁挖矿时每个方块独立暴击判定，Title 显示暴击方块数和额外金币

**PAPI 占位符**：`%goldminer_crit_hit_rate%` / `%goldminer_crit_magnification%`

### 药水商店

在矿场商店 → 购买增幅 → 药水效果：

| 药水 | 等级范围 | 时长选项 |
|------|----------|----------|
| 急迫 | 1~30级 | 30秒~1小时 |
| 速度 | 1~30级 | 同上 |
| 幸运 | 1~30级 | 同上 |

- 购买后覆盖当前同类型效果
- 价格公式：`基础价格 × (1 + 等级 × 等级倍率) × (1 + 时长指数 × 时长倍率)`
- 所有参数可在 `shop.yml` 调整

### 暴击率/暴击倍率商店

- **暴击率**：永久提升，可选 +0.5% / 1% / 5% / 10% / 50%
- **暴击倍率**：30 分钟临时提升，可选 1~20 倍，重复购买叠加时长和倍率
- 暴击率满 100% 后购买溢出部分按 500% 转换为经验值

### 等级购买

- 预设：购买 1 级 / 5 级 / 10 级
- 精确：`/goldminer buy lv <数量> confirm`
- 价格公式：`(当前等级→目标等级所需总经验) × 经验单价系数`
- 公式在 GUI 中公示

### 连锁体验卡

在矿场商店 → 连锁体验卡（矿场世界内生效，30秒）：

| 类型 | 说明 | 价格公式 |
|------|------|----------|
| 平面X轴连锁 | 沿X轴扩展，最高15方块 | 8000 × 3.0^(N-1) |
| 平面Z轴连锁 | 沿Z轴扩展，最高15方块 | 8000 × 3.0^(N-1) |
| 半径范围连锁 | 球形范围，最高15半径 | 15000 × 3.5^(N-1) |
| 视角方向连锁 | 视线前方，最高15方块+15高度 | 6000 × 2.5^(N-1) |

- 点击进入调配界面：`◀ 范围减` / `N方块` / `▶ 范围增`
- 视角方向连锁额外有高度调节（`◀ 高度减` / `N格` / `▶ 高度增`）
- 调节时界面原地刷新，光标位置不变
- **同类型**：叠加范围+高度+时长
- **不同类型**：覆盖旧效果，不返还金币

### 一般连锁

在矿场商店 → 一般连锁（矿场世界外全地图生效）：

- **价格**：100 金币
- **时长**：3 小时（可叠加）
- **范围**：9×9×3 同类型方块
- 挖掘矿石/木头时自动连锁同类型相邻方块
- 连锁掉落受玩家工具附魔影响（时运等）
- 矿场世界内不生效（互不干扰）

### 小队系统

- `/goldminer team create` 创建小队（聊天栏输入名称）
- `/goldminer team join <队名>` 申请加入
- 队长 `/goldminer team accept [玩家名]` 接受申请
- 退出小队 → 经验/等级/镐子清空（金币保留）
- 成员上限 10 人

### 货币兑换

- 矿场金币可兑换为主世界货币（需 Vault）
- 汇率：`1 矿场金币 = X 主世界货币`（config.yml 可调）
- GUI 提供 10/100/1000 快捷兑换，也支持聊天栏输入自定义数量

---

## 权限节点

| 权限 | 说明 | 默认 |
|------|------|------|
| `goldminer.join` | 加入矿场 | true |
| `goldminer.suit` | 切换装备显示 | true |
| `goldminer.team.create` | 创建小队 | true |
| `goldminer.team.join` | 加入小队 | true |
| `goldminer.team.accept` | 接受入队申请 | true |
| `goldminer.team.leave` | 退出小队 | true |
| `goldminer.team.list` | 查看小队列表 | true |
| `goldminer.top` | 查看排行榜 | true |
| `goldminer.exchange` | 货币兑换 | true |
| `goldminer.admin` | 管理员权限 | op |

---

## 常见问题

**Q：加入矿场后看不到矿物？**
A：矿场为 100×100×100 立方体，从 y=0 到 y=100。请确认你所处位置在矿场范围内。

**Q：矿场不自动刷新？**
A：检查 `config.yml` 中 `mine.refresh-interval` 是否设置为有效值。可执行 `/goldminer reload pool` 手动刷新。

**Q：升级后镐子没有变化？**
A：镐子在快捷栏第 1 格，升级后自动替换。如有旧镐子残留，重新 `/goldminer join` 即可。

**Q：连锁体验卡到期后还能连锁吗？**
A：不能。连锁体验卡仅在购买后 30 秒内生效，到期自动失效。

**Q：一般连锁在矿场世界内生效吗？**
A：不生效。矿场世界内请购买连锁体验卡。一般连锁仅在矿场外的世界生效。

**Q：如何修改商店价格？**
A：方式一：直接编辑 `shop.yml` 后 `/goldminer reload`。方式二：`/goldminer shop set <key> <价格>` 即时生效。

**Q：PAPI 占位符不显示？**
A：确保已安装 PlaceholderAPI 插件，未安装时占位符静默忽略不影响插件运行。

**Q：服务器重启后矿场不刷新？**
A：插件会自动重建矿场方块追踪列表，首次刷新生效后恢复正常。

---

**作者**: 未定awa  
**版本**: 2.1.3  
**兼容**: Purpur 1.21.11
