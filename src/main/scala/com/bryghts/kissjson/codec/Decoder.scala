package com.bryghts.kissjson
package codec

import scala.reflect.runtime.universe._
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import scala.reflect.ClassTag

import scala.language.reflectiveCalls

trait Decoder[T]
{


	private[codec] def genCollection(t: Type, v: List[_]): Any =
	{
		val m = runtimeMirror(t.getClass.getClassLoader)
		def classTag[T](t: Type): ClassTag[T] = {
			val rtc = m.runtimeClass(t)
			ClassTag(rtc)
		}

		val s = t.typeSymbol.companionSymbol
		val mthd =
					s
							.asModule
							.moduleClass
							.asClass
							.typeSignature
							.member(newTermName("apply"))
							.asTerm
							.alternatives
							.map{_.asMethod}
							.filter{m => m.paramss.length == 1 && m.paramss.head.length == 1}
							.head

		val i = m.reflectModule(s.asModule).instance
		val im = m.reflect(i)
		val mm = im.reflectMethod(mthd.asMethod)

		mm(v)
	}

	private[codec] def subType(t: Type) =
			t.asInstanceOf[TypeRef].args(0)

	def decode(v: JsonValue, t: Type)(implicit env: DecoderEnvironment): Option[Try[T]]

	final def decode(v: JsonValue)(implicit t: TypeTag[T], env: DecoderEnvironment): Option[Try[T]] =
		decode(v, t.tpe)(env)

}


case class SimpleDecoder[Source <: JsonValue : TypeTag, Target: TypeTag](validateJsonValue: JsonValue => Boolean, toSource: JsonValue => Source, sourceToTarget: Source => Target) extends Decoder[Target]
{

	def decode(v: JsonValue, t: Type)(implicit env: DecoderEnvironment): Option[Try[Target]] =
	{
		if(t <:< typeOf[Target])
			if(validateJsonValue(v))
				Some(Success(sourceToTarget(toSource(v))))
			else
				Some(Failure(new Exception("Invalid type of value")))
		else
			None
	}

}

case class OptionDecoder[T](implicit internalDecoder: Decoder[T], it: TypeTag[T]) extends Decoder[Option[T]]
{
	def decode(v: JsonValue, t: Type)(implicit env: DecoderEnvironment): Option[Try[Option[T]]] =
		decodeInternal(v)(env)

	def decodeInternal(v: JsonValue)(implicit env: DecoderEnvironment): Option[Try[Option[T]]] =
		if(v == JsonNull) Some(Success(None))
		else
			internalDecoder.decode(v, typeOf[T])(env) match {
				case Some(Failure(t))  => Some(Failure(t))
				case Some(Success(r))  => Some(Success(Some(r)))
				case _                 => None
			}

}

object GenericOptionDecoder extends Decoder[Option[_]]
{


	def decode(v: JsonValue, t: Type)(implicit env: DecoderEnvironment): Option[Try[Option[Any]]] =
		if(t <:< typeOf[Option[_]])
		{
			if(v == JsonNull || v == null) Some(Success(None))
			else
			{
				val d = tryToDecode(v, subType(t), env)

				d match {
					case Some(Failure(t)) => Some(Failure(t))
					case Some(Success(r)) => Some(Success(Some(r)))
					case _                => None
				}
			}
		}
		else None
}

case class ArrayDecoder[T](implicit internalDecoder: Decoder[T], it: TypeTag[T], ct: ClassTag[T]) extends Decoder[Array[T]]
{
	def decode(in: JsonValue, t: Type)(implicit env: DecoderEnvironment): Option[Try[Array[T]]] =
	{
		if(t <:< typeOf[Array[_]])
		{
			in match {
				case v: JsonArray[_] =>
					val r: Array[T] = new Array[T](v.length)
		
					v.toList.map{tryToDecode(_, it.tpe, env)}.zipWithIndex.foreach{case (b, i) =>
							r(i) =
								b match {
									case Some(Failure(t)) =>
										return Some(Failure(t))
									case Some(Success(p)) =>
										p.asInstanceOf[T]
									case _ => return Some(Failure(new Exception("No Decoder found")))
								}
					}
					Some(Success(r))
				case _ => None
			}
		}
		else
			None
	}

}

object GenericArrayDecoder extends Decoder[Array[_]]
{

	private def doDecode[T](in: JsonValue, t: Type)(implicit env: DecoderEnvironment, ct: ClassTag[T]): Option[Try[Array[_]]] =
	{
		in match {
			case v: JsonArray[_] =>
				val r: Array[T] = new Array[T](v.length)
		
				v.toList.map{tryToDecode(_, subType(t), env)}.zipWithIndex.foreach{case (b, i) =>
						r(i) =
							b match {
								case Some(Failure(t)) =>
									return Some(Failure(t))
								case Some(Success(p)) =>
									p.asInstanceOf[T]
								case _ => return Some(Failure(new Exception("No Decoder found")))
							}
				}
				Some(Success(r))
			case _ => None
		}
	}

	def decode(v: JsonValue, t: Type)(implicit env: DecoderEnvironment): Option[Try[Array[_]]] = {
		val m = runtimeMirror(t.getClass.getClassLoader)
		def classTag[T](t: Type): ClassTag[T] = {
			val rtc = m.runtimeClass(t)
			ClassTag(rtc)
		}

		if(t <:< typeOf[Array[_]])
			doDecode(v, t)(env, classTag(subType(t)))
		else
			None
	}
}


class TraversableDecoder[C <: Traversable[_]](implicit ct : TypeTag[C]) extends Decoder[C]
{
	def decode(in: JsonValue, t: Type)(implicit env: DecoderEnvironment): Option[Try[C]] =
	{
		if(t <:< typeOf[C])
		{
			in match {
				case v: JsonArray[_] =>
					val r = v.toList.map{tryToDecode(_, subType(t), env)}.zipWithIndex.map{case (b, i) =>
								b match {
									case Some(Failure(t)) =>
										return Some(Failure(t))
									case Some(Success(p)) => p
									case _ => return Some(Failure(new Exception("No Decoder found")))
								}
					}
		
					Some(Success(genCollection(t, r).asInstanceOf[C]))
				case _ => None
			}
		}
		else
			None
	}

}

//class TraversableDecoder[T, C <: Traversable[T]](implicit internalDecoder: Decoder[T], it: TypeTag[T], ct : TypeTag[C]) extends Decoder[C]
//{
//	def decode(v: JsonValue, t: Type)(implicit env: DecoderEnvironment): Option[Try[C]] =
//	{
//		if(t <:< typeOf[C])
//		{
//			val r = v.toList.map{tryToDecode(_, it.tpe, env, env)}.zipWithIndex.map{case (b, i) =>
//						b match {
//							case Some(Failure(t)) =>
//								return Some(Failure(t))
//							case Some(Success(p)) => p
//							case _ => return Some(Failure(new Exception("No Decoder found")))
//						}
//			}
//
//			Some(Success(genCollection(t, r).asInstanceOf[C]))
//		}
//		else
//			None
//	}
//
//}

object GenericTraversableDecoder extends Decoder[Traversable[_]]
{

	def decode(in: JsonValue, t: Type)(implicit env: DecoderEnvironment): Option[Try[Traversable[_]]] =
	{
		if(t <:< typeOf[Traversable[_]])
		{
			in match {
				case v: JsonArray[_] =>
					val r = v.toList.map{tryToDecode(_, subType(t), env)}.zipWithIndex.map{case (b, i) =>
								b match {
									case Some(Failure(t)) =>
										return Some(Failure(t))
									case Some(Success(p)) => p
									case _ => return Some(Failure(new Exception("No Decoder found")))
								}
					}
		
					Some(Success(genCollection(t, r).asInstanceOf[Traversable[_]]))
				case _ => None
			}
		}
		else
			None
	}

}

object CaseClassDecoder extends Decoder[Product]
{

	def decode(v: JsonValue, t: Type)(implicit env: DecoderEnvironment): Option[Try[Product]] =
		v match {
			case jo: JsonObject => doDecode(jo, t)(env)
			case _ => Some(Failure(new Exception("Invalid type of value")))
		}

	private def doDecode(v: JsonObject, t: Type)(implicit env: DecoderEnvironment): Option[Try[Product]] =
	{
		val m = runtimeMirror(t.getClass.getClassLoader)
		def classTag[T](t: Type): ClassTag[T] = {
			val rtc = m.runtimeClass(t)
			ClassTag(rtc)
		}


		if(!t.typeSymbol.asClass.isCaseClass)    return None

		val ctor = t.declaration(nme.CONSTRUCTOR).asMethod
		val cm = m.reflectClass(t.typeSymbol.asClass)
		val ctorm = cm.reflectConstructor(ctor)

		val params = ctor.paramss.map{_.map{p =>

			val rt = p.typeSignatureIn(t)


			val f = v.asMap.getOrElse(p.name.decoded, JsonNull)

			tryToDecode(f, rt, env)
		}}

		if(params.flatten.exists(f => f == None || f.get.isFailure))
			return Some(Failure(new Exception("")))

		Some(Success(ctorm(params.flatten.map{_.get.get} :_*).asInstanceOf[Product]))

	}

}
