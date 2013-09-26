package org.overviewproject.postgres

import org.overviewproject.test.DbSpecification
import org.postgresql.PGConnection
import org.overviewproject.database.DB

class LargeObjectInputStreamSpec extends DbSpecification {

  step(setupDb)

  "LargeObjectInputStream" should {

    trait LoContext extends DbTestContext {
      implicit var pgConnection: PGConnection = _
      val data = Array.tabulate[Byte](100)(i => i.toByte)
      val BufferSize: Int = 10
      
      var loInputStream: LargeObjectInputStream = _
      
      override def setupWithDb = {
        pgConnection = DB.pgConnection
        LO.withLargeObject { largeObject =>
          largeObject.add(data)
          loInputStream = new LargeObjectInputStream(largeObject.oid, BufferSize)
        }
      }
    }

    trait LoWith255 extends LoContext {
      override val data = Array[Byte](255.toByte)
    }
    
    trait LoWithSmallData extends LoContext {
      override val data = Array.tabulate[Byte](BufferSize - 2)(i => i.toByte)
    }
    
    "read one byte at a time from chunk" in new LoContext {
      val readData = Array.fill(BufferSize)(loInputStream.read.toByte)
       
      readData must be equalTo data.take(BufferSize)
    }
    
    "read beyond buffer size" in new LoContext {
      val readData = new Array[Byte](100)
      
      loInputStream.read(readData, 0, 100) must be equalTo 100
      
      readData must be equalTo data
    }
    
    "read beyond the end of the LargeObject" in new LoContext {
      val readData = new Array[Byte](300)
      
      loInputStream.read(readData, 0, 200) must be equalTo 100
      
      readData.take(100) must be equalTo data
      
      loInputStream.read(readData, 0, 100) must be equalTo -1
    }
    
    "close stream" in new LoContext {
      loInputStream.close()
      loInputStream.read must throwA[java.io.IOException]
    }

    "mark and reset stream" in new LoContext {
      val readData = new Array[Byte](40)

      loInputStream.read(readData) // pos = 40
      loInputStream.mark(40)       // pos = 40
      loInputStream.read(readData) // pos = 80
      loInputStream.reset()        // pos = 40
      val nRead = loInputStream.read(readData)

      nRead must be equalTo readData.length
      readData(0) must be equalTo(40)
    }
    
    "throw IOException on error" in new DbTestContext {
      val loInputStream = new LargeObjectInputStream(-1)
      loInputStream.read must throwA[java.io.IOException]
    }
    
    "not read 255 as end of stream" in new LoWith255 {
      val b = loInputStream.read
      b must be equalTo 0x00ff
    }
    
    "read beyond data, but not beyond buffer" in new LoWith255 {
      val readData = new Array[Byte](10)
      
      loInputStream.read(readData, 0, 5) must be equalTo 1
    }
    
    "read buffer in multiple chunks" in new LoWithSmallData {
      val readData = new Array[Byte](10)
      loInputStream.read(readData, 0, 5) must be equalTo 5
      loInputStream.read(readData, 5, 4) must be equalTo 3
      
      readData.take(8) must be equalTo data.take(8)
    }
    
    "read beyond buffer in multiple chunks" in new LoContext {
      val readData = new Array[Byte](20)
      loInputStream.read(readData, 0, 5) must be equalTo 5
      loInputStream.read(readData, 5, 15) must be equalTo 15
      
      readData must be equalTo data.take(20)
    }
    
    
  }
  step(shutdownDb)
}
