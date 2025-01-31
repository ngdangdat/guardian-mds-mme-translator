package com.guardian
package repo

import AppError.{SecondNotFound, SymbolNotFound}
import Config.{Channel, RedisConfig}
import entity._
import repo.InMemImpl._

import cats.data.EitherT
import cats.implicits.{catsSyntaxApplicativeId, catsSyntaxEitherId, toTraverseOps}
import io.lettuce.core.RedisClient
import monix.eval.Task
import org.apache.logging.log4j.scala.Logging

abstract class Store(val channel: Channel) extends Logging {
  import Store._

  def connect(): Task[Either[AppError, Unit]]

  def disconnect: Task[Either[AppError, Unit]]

  def saveSecond(unixSecond: Int): Task[Either[AppError, Unit]]

  def getSecond: Task[Either[AppError, Int]]

  def saveTradableInstrument(
      oid: OrderbookId,
      symbol: Instrument,
      secType: String,
      secDesc: String,
      allowShortSell: Byte,
      allowNVDR: Byte,
      allowShortSellOnNVDR: Byte,
      allowTTF: Byte,
      isValidForTrading: Byte,
      lotRoundSize: Int,
      parValue: Long,
      sectorNumber: String,
      underlyingSecCode: Int,
      underlyingSecName: String,
      maturityDate: Int,
      contractMultiplier: Int,
      settlMethod: String,
      decimalsInPrice: Short,
      marketTs: Nano
  ): Task[Either[AppError, Unit]]
  def getInstrument(orderbookId: OrderbookId): Task[Either[AppError, Instrument]]

  def updateOrderbook(
      seq: Long,
      orderbookId: OrderbookId,
      acts: Seq[FlatPriceLevelAction],
      decimalsInPrice: Short
  ): Task[Either[AppError, Unit]] =
    (for {
      ref       <- EitherT.rightT[Task, AppError](acts.head)
      maybeItem <- EitherT(getLastOrderbookItem(ref.symbol, decimalsInPrice))
      update    <- EitherT(updateOrderbookAggregate(seq, maybeItem, acts))
      _         <- EitherT(saveOrderbookItem(ref.symbol, orderbookId, update, decimalsInPrice))
    } yield ()).value

  def updateOrderbookAggregate(
      seq: Long,
      lastItem: Option[OrderbookItem],
      items: Seq[FlatPriceLevelAction]
  ): Task[Either[AppError, OrderbookItem]] =
    (for {
      ref <- EitherT.rightT[Task, AppError](items.head)
      start <-
        if (lastItem.isDefined) {
          EitherT.rightT[Task, AppError](lastItem.get)
        }
        else {
          EitherT.rightT[Task, AppError](OrderbookItem.empty(ref.maxLevel))
        }
      update <- EitherT.rightT[Task, AppError](items.indices.foldLeft(start)((cur, index) => {
        val item          = items(index)
        val nuPriceLevels = buildPriceLevels(cur, item)
        if (nuPriceLevels.isLeft) {
          // THIS MODIFICATION IS ONLY TO AVOID DUPLICATE ENTRIES, IT SHOULD ONLY BE cur
          val m = nuPriceLevels.swap.map(p => s"""
               |${p.msg}
               |$item
               |""".stripMargin).getOrElse("Duplicate error")
          logger.info(m)
          cur.copy(seq = seq)
        }
        else {
          OrderbookItem.reconstruct(
            nuPriceLevels.toOption.get,
            side = item.side.value,
            seq = seq,
            maxLevel = item.maxLevel,
            marketTs = item.marketTs,
            bananaTs = item.bananaTs,
            origin = cur
          )
        }
      }))
    } yield update).value

  def getLastOrderbookItem(symbol: Instrument, decimalsInPrice: Short): Task[Either[AppError, Option[OrderbookItem]]]

  def saveOrderbookItem(
      symbol: Instrument,
      orderbookId: OrderbookId,
      item: OrderbookItem,
      decimalsInPrice: Short
  ): Task[Either[AppError, Unit]]

  def getLastTickerTotalQty(symbol: Instrument): Task[Either[AppError, Qty]]

  def saveTicker(
      oid: OrderbookId,
      symbol: Instrument,
      seq: Long,
      p: Price,
      q: Qty,
      aggressor: Byte,
      tq: Qty,
      dealSource: Byte,
      action: Byte,
      tradeReportCode: Short,
      dealDateTime: Long,
      decimalsInPrice: Short,
      marketTs: Nano,
      bananaTs: Micro
  ): Task[Either[AppError, Unit]]

  def updateTicker(
      oid: OrderbookId,
      symbol: Instrument,
      seq: Long,
      p: Price,
      q: Qty,
      aggressor: Byte,
      dealSource: Byte,
      action: Byte,
      tradeReportCode: Short,
      dealDateTime: Long,
      decimalsInPrice: Short,
      marketTs: Nano,
      bananaTs: Micro
  ): Task[Either[AppError, Unit]]

  def updateProjected(
      oid: OrderbookId,
      symbol: Instrument,
      seq: Long,
      p: Price,
      q: Qty,
      ib: Qty,
      decimalsInPrice: Short,
      marketTs: Nano,
      bananaTs: Micro
  ): Task[Either[AppError, Unit]]

  def updateTradeStat(
      oid: OrderbookId,
      symbol: Instrument,
      seq: Long,
      o: Price,
      h: Price,
      l: Price,
      c: Price,
      lastAuctionPx: Price,
      avgpx: Price,
      turnOverQty: Qty,
      turnOverVal: Price8,
      reportedTurnOverQty: Qty,
      reportedTurnOverVal: Price8,
      totalNumberTrades: Qty,
      decimalsInPrice: Short,
      marketTs: Nano,
      bananaTs: Micro
  ): Task[Either[AppError, Unit]]

  def updateMarketStats(
      oid: OrderbookId,
      symbol: Instrument,
      seq: Long,
      o: Price8,
      h: Price8,
      l: Price8,
      c: Price8,
      previousClose: Price8,
      tradedVol: Qty,
      tradedValue: Price8,
      change: Price8,
      changePercent: Int,
      tradeTs: Long,
      decimalsInPrice: Short,
      marketTs: Nano,
      bananaTs: Micro
  ): Task[Either[AppError, Unit]]

  def updateMySqlIPOPrice(
      oid: OrderbookId,
      ipoPrice: Price,
      decimalsInPrice: Short
  ): Task[Either[AppError, Unit]]

  def updateMySqlSettlementPrice(
      oid: OrderbookId,
      marketTs: Nano,
      settlPrice: Price,
      decimalsInPrice: Short
  ): Task[Either[AppError, Unit]]

  def updateReferencePrice(
      oid: OrderbookId,
      symbol: Instrument,
      priceType: Byte,
      price: Price,
      decimalsInPrice: Short,
      marketTs: Nano,
      bananaTs: Micro
  ): Task[Either[AppError, Unit]]

  def saveDecimalsInPrice(oid: OrderbookId, d: Short): Task[Either[AppError, Unit]]
  def getDecimalsInPrice(oid: OrderbookId): Task[Either[AppError, Short]]
}

class InMemImpl(channel: Channel) extends Store(channel) {

  private var marketSecondDb: Option[Int]                                    = None
  private var symbolReferenceDb: Map[OrderbookId, Instrument]                = Map.empty
  private var orderbookDb: Map[String, Vector[OrderbookItem]]                = Map.empty
  private var tickerDb: Map[String, Vector[TickerItem]]                      = Map.empty
  private var projectedDb: Map[String, Vector[ProjectedItem]]                = Map.empty
  var klineDb: Map[String, Vector[KlineItem]]                                = Map.empty
  private var marketStatsDb: Map[String, Vector[MarketStatsItem]]            = Map.empty
  private var decimalsInPriceDb: Map[OrderbookId, Short]                     = Map.empty
  private var referencePriceDb: Map[OrderbookId, Vector[ReferencePriceItem]] = Map.empty
  override def connect(): Task[Either[AppError, Unit]]                       = ().asRight.pure[Task]

  override def disconnect: Task[Either[AppError, Unit]] = ().asRight.pure[Task]

  override def saveTradableInstrument(
      oid: OrderbookId,
      symbol: Instrument,
      secType: String,
      secDesc: String,
      allowShortSell: Byte,
      allowNVDR: Byte,
      allowShortSellOnNVDR: Byte,
      allowTTF: Byte,
      isValidForTrading: Byte,
      lotRoundSize: Int,
      parValue: Long,
      sectorNumber: String,
      underlyingSecCode: Int,
      underlyingSecName: String,
      maturityDate: Int,
      contractMultiplier: Int,
      settlMethod: String,
      decimalsInPrice: Short,
      marketTs: Nano
  ): Task[Either[AppError, Unit]] = {
    symbolReferenceDb = symbolReferenceDb + (oid -> symbol)
    ().asRight.pure[Task]
  }

  override def getInstrument(oid: OrderbookId): Task[Either[AppError, Instrument]] =
    symbolReferenceDb.get(oid).fold(SymbolNotFound(oid).asLeft[Instrument])(p => p.asRight).pure[Task]

  override def saveOrderbookItem(
      symbol: Instrument,
      orderbookId: OrderbookId,
      item: OrderbookItem,
      decimalsInPrice: Short
  ): Task[Either[AppError, Unit]] = {
    val list = orderbookDb.getOrElse(symbol.value, Vector.empty)
    orderbookDb += (symbol.value -> (Vector(item) ++ list))
    ().asRight[AppError].pure[Task]
  }

  override def getLastOrderbookItem(
      symbol: Instrument,
      decimalsInPrice: Short
  ): Task[Either[AppError, Option[OrderbookItem]]] =
    orderbookDb.getOrElse(symbol.value, Vector.empty).sortWith(_.seq > _.seq).headOption.asRight.pure[Task]

  override def updateTicker(
      oid: OrderbookId,
      symbol: Instrument,
      seq: Long,
      p: Price,
      q: Qty,
      aggressor: Byte,
      dealSource: Byte,
      action: Byte,
      tradeReportCode: Short,
      dealDateTime: Long,
      decimalsInPrice: Short,
      marketTs: Nano,
      bananaTs: Micro
  ): Task[Either[AppError, Unit]] =
    (for {
      lastTq <- EitherT(getLastTickerTotalQty(symbol))
      newTq <- EitherT.rightT[Task, AppError](dealSource match {
        case 3 => lastTq // Trade Report
        case _ => Qty(lastTq.value + q.value)
      })
      _ <- EitherT(
        saveTicker(
          oid = oid,
          symbol = symbol,
          seq = seq,
          p = p,
          q = q,
          tq = newTq,
          aggressor = aggressor,
          dealSource = dealSource,
          action = action,
          tradeReportCode = tradeReportCode,
          dealDateTime = dealDateTime,
          decimalsInPrice = decimalsInPrice,
          marketTs = marketTs,
          bananaTs = bananaTs
        )
      )
    } yield ()).value
  override def saveSecond(unixSecond: Int): Task[Either[AppError, Unit]] = {
    marketSecondDb = Some(unixSecond)
    ().asRight.pure[Task]
  }

  override def getSecond: Task[Either[AppError, Int]] =
    marketSecondDb.fold(SecondNotFound.asLeft[Int])(p => p.asRight).pure[Task]

  override def getLastTickerTotalQty(symbol: Instrument): Task[Either[AppError, Qty]] =
    tickerDb
      .getOrElse(symbol.value, Vector.empty)
      .sortWith(_.seq > _.seq)
      .headOption
      .map(_.tq)
      .getOrElse(Qty(0L))
      .asRight
      .pure[Task]

  override def saveTicker(
      oid: OrderbookId,
      symbol: Instrument,
      seq: Long,
      p: Price,
      q: Qty,
      aggressor: Byte,
      tq: Qty,
      dealSource: Byte,
      action: Byte,
      tradeReportCode: Short,
      dealDateTime: Long,
      decimalsInPrice: Short,
      marketTs: Nano,
      bananaTs: Micro
  ): Task[Either[AppError, Unit]] = {
    val list = tickerDb.getOrElse(symbol.value, Vector.empty)
    tickerDb += (symbol.value -> (Vector(
      TickerItem(
        seq = seq,
        p = p,
        q = q,
        aggressor = aggressor,
        tq = tq,
        dealSource = dealSource,
        action = action,
        tradeReportCode = tradeReportCode,
        marketTs = marketTs,
        bananaTs = bananaTs
      )
    ) ++ list))
    ().asRight[AppError].pure[Task]
  }

  override def updateProjected(
      oid: OrderbookId,
      symbol: Instrument,
      seq: Long,
      p: Price,
      q: Qty,
      ib: Qty,
      decimalsInPrice: Short,
      marketTs: Nano,
      bananaTs: Micro
  ): Task[Either[AppError, Unit]] = {
    val list = projectedDb.getOrElse(symbol.value, Vector.empty)
    projectedDb += (symbol.value -> (Vector(
      ProjectedItem(
        seq = seq,
        p = p,
        q = q,
        ib = ib,
        marketTs = marketTs,
        bananaTs = bananaTs
      )
    ) ++ list))
    ().asRight[AppError].pure[Task]
  }

  override def updateTradeStat(
      oid: OrderbookId,
      symbol: Instrument,
      seq: Long,
      o: Price,
      h: Price,
      l: Price,
      c: Price,
      lastAuctionPx: Price,
      avgpx: Price,
      turnOverQty: Qty,
      turnOverVal: Price8,
      reportedTurnOverQty: Qty,
      reportedTurnOverVal: Price8,
      totalNumberTrades: Qty,
      decimalsInPrice: Short,
      marketTs: Nano,
      bananaTs: Micro
  ): Task[Either[AppError, Unit]] = {
    val list = klineDb.getOrElse(symbol.value, Vector.empty)
    klineDb += (symbol.value -> (Vector(
      KlineItem(
        seq = seq,
        o = o,
        h = h,
        l = l,
        c = c,
        lauctpx = lastAuctionPx,
        turnOverQty = turnOverQty,
        turnOverVal = turnOverVal,
        reportedTurnOverQty = reportedTurnOverQty,
        reportedTurnOverVal = reportedTurnOverVal,
        totalNumberTrades = totalNumberTrades,
        avgpx = avgpx,
        marketTs = marketTs,
        bananaTs = bananaTs
      )
    ) ++ list))
    ().asRight[AppError].pure[Task]
  }

  override def updateMarketStats(
      oid: OrderbookId,
      symbol: Instrument,
      seq: Long,
      o: Price8,
      h: Price8,
      l: Price8,
      c: Price8,
      previousClose: Price8,
      tradedVol: Qty,
      tradedValue: Price8,
      change: Price8,
      changePercent: Int,
      tradeTs: Long,
      decimalsInPrice: Short,
      marketTs: Nano,
      bananaTs: Micro
  ): Task[Either[AppError, Unit]] = {
    val list = marketStatsDb.getOrElse(symbol.value, Vector.empty)
    marketStatsDb += (symbol.value -> (Vector(
      MarketStatsItem(
        seq = seq,
        o = o,
        h = h,
        l = l,
        c = c,
        previousClose = previousClose,
        tradedVol = tradedVol,
        tradedValue = tradedValue,
        change = change,
        changePercent = changePercent,
        tradeTs = tradeTs,
        marketTs = marketTs,
        bananaTs = bananaTs
      )
    ) ++ list))
    ().asRight[AppError].pure[Task]
  }

  override def updateMySqlIPOPrice(
      oid: OrderbookId,
      ipoPrice: Price,
      decimalsInPrice: Short
  ): Task[Either[AppError, Unit]] = ().asRight.pure[Task] // UNUSED

  override def updateMySqlSettlementPrice(
      oid: OrderbookId,
      marketTs: Nano,
      settlPrice: Price,
      decimalsInPrice: Short
  ): Task[Either[AppError, Unit]] =
    ().asRight.pure[Task] // UNUSED

  override def getDecimalsInPrice(oid: OrderbookId): Task[Either[AppError, Short]] =
    decimalsInPriceDb.getOrElse(oid, 1.asInstanceOf[Short]).asRight.pure[Task]

  override def saveDecimalsInPrice(oid: OrderbookId, d: Short): Task[Either[AppError, Unit]] = {
    decimalsInPriceDb += (oid -> d)
    ().asRight.pure[Task]
  }

  override def updateReferencePrice(
      oid: OrderbookId,
      symbol: Instrument,
      priceType: Byte,
      price: Price,
      decimalsInPrice: Short,
      marketTs: Nano,
      bananaTs: Micro
  ): Task[Either[AppError, Unit]] = {
    val list = referencePriceDb.getOrElse(oid, Vector.empty)
    referencePriceDb += (oid -> (Vector(
      ReferencePriceItem(
        oid = oid,
        priceType = priceType,
        price = price,
        marketTs = marketTs,
        bananaTs = bananaTs
      )
    ) ++ list))
    ().asRight.pure[Task]
  }
}

object InMemImpl {
  case class TickerItem(
      seq: Long,
      p: Price,
      q: Qty,
      aggressor: Byte,
      tq: Qty,
      dealSource: Byte,
      action: Byte,
      tradeReportCode: Short,
      marketTs: Nano,
      bananaTs: Micro
  )
  case class ProjectedItem(
      seq: Long,
      p: Price,
      q: Qty,
      ib: Qty,
      marketTs: Nano,
      bananaTs: Micro
  )

  case class KlineItem(
      seq: Long,
      o: Price,
      h: Price,
      l: Price,
      c: Price,
      lauctpx: Price,
      avgpx: Price,
      turnOverQty: Qty,
      turnOverVal: Price8,
      reportedTurnOverQty: Qty,
      reportedTurnOverVal: Price8,
      totalNumberTrades: Qty,
      marketTs: Nano,
      bananaTs: Micro
  )
  case class MarketStatsItem(
      seq: Long,
      o: Price8,
      h: Price8,
      l: Price8,
      c: Price8,
      previousClose: Price8,
      tradedVol: Qty,
      tradedValue: Price8,
      change: Price8,
      changePercent: Int,
      tradeTs: Long,
      marketTs: Nano,
      bananaTs: Micro
  )

  case class ReferencePriceItem(
      oid: OrderbookId,
      priceType: Byte,
      price: Price,
      marketTs: Nano,
      bananaTs: Micro
  )
}

object Store {

  def buildPriceLevels(
      base: OrderbookItem,
      item: FlatPriceLevelAction
  ): Either[AppError, Seq[Option[(Price, Qty, Nano)]]] =
    item.levelUpdateAction match {
      case 'N' => base.insert(item.price, item.qty, item.marketTs, item.level, item.side).asRight
      case 'D' => base.delete(side = item.side, level = item.level, numDeletes = item.numDeletes).asRight
      case _ =>
        base.update(side = item.side, level = item.level, price = item.price, qty = item.qty, marketTs = item.marketTs)
    }

  def intToBigDecimal(i: Int, decimals: Short): BigDecimal   = BigDecimal(i / Math.pow(10, decimals))
  def longToBigDecimal(l: Long, decimals: Short): BigDecimal = BigDecimal(l / Math.pow(10, decimals))
  def bigDecimalToInt(b: BigDecimal, decimals: Short): Int   = Math.round((b * Math.pow(10, decimals)).toFloat)

  def redis(channel: Channel, redisConfig: RedisConfig): Store =
    new RedisImpl(
      channel,
      RedisClient.create(s"""redis://${redisConfig.password.fold("")(p => s":$p@")}${redisConfig.host}:${redisConfig.port}/${redisConfig.number.getOrElse("0")}""")
    )
}
