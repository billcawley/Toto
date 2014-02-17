Attribute VB_Name = "Azquo"
'This code, and all code associated with the Azquo workbook, copyright Azquo Ltd 2013.  Programmed by Bill Cawley

Option Explicit

Global azError
Global azConnectionId
Global azNameChosen
Global azResponse
Public az_RgtClkMenu As CommandBar
Public azName As Object

Sub Auto_open()

    azConnectionId = ""
    azError = ""

End Sub




Function logon()
   LogonForm.Show
   If azConnectionId = "aborted" Then
     End
   End If
End Function

Sub CheckConnectionId()
Dim LogonOk As Boolean
Dim result As String

LogonOk = False
 While (azConnectionId = "")
     logon
     LogonOk = True
  Wend
  
 While (Not LogonOk)
    Dim params As String
     params = "checkconnectionid=" & azConnectionId
    result = AzquoPost1("Login", params)
  If (result = "ok") Then
     LogonOk = True
     Else
          logon
  End If
  Wend
End Sub



Sub ZapSheet(SheetName)
   Dim SheetNo As Worksheet
   
   For Each SheetNo In ActiveWorkbook.Worksheets
      If SheetNo.Name = SheetName Then
         Application.DisplayAlerts = False
          SheetNo.Delete
          Application.DisplayAlerts = True
       End If
    Next
End Sub

Sub AddSheet(SheetName)
    ZapSheet (SheetName)
    Sheets.Add
    ActiveSheet.Name = SheetName
End Sub

  
  
  
 
Function CheckRange(RangeName)

 Dim RName As Name
 
  For Each RName In Sheets(1).Names
     If LCase(RName) = LCase(RangeName) Then
        CheckRange = True
        Exit Function
     End If
  Next
  CheckRange = False
  azError = "Range " & RangeName & " not found"
End Function
 End Function

Function checkWidth(ArraySent)
   pos = 0
   eol = Find("\n", ArraySent)
   If (eol < 0) Then
      eol = Len(ArraySent)
   End If
   Count = 1
   While (Find("\t", ArraySent, pos) > 0 And pos < eol)
     pos = Find("\t", ArraySent, pos) + 1
     Count = Count + 1
  Wend
  checkWidth = Count
End Function

Function checkHeight(ArraySent)
   pos = 0
   Count = 1
   While (Find("\n", ArraySent, pos) > 0 And pos < eol)
     pos = Find("\t", ArraySent, pos) + 1
     Count = Count + 1
  Wend
  checkHeight = Count
End Function


Function AzquoPost(strURL, strPostData)
  CheckConnectionId
  AzquoPost = AzquoPost1(strURL, strPostData)

End Function

Function AzquoPost1(strURL, strPostData)
    If strURL = "Name" Then
       strPostData = "json={""connectionId"":""" & azConnectionId & """," & strPostData & "}"
    Else
       strPostData = "connectionid=" & azConnectionId & "&" & strPostData
    End If
    Dim pXmlHttp As Object
    Set pXmlHttp = CreateObject("MSXML2.XMLHTTP")
      pXmlHttp.Open "POST", "https://data.azquo.com:8443/api/" & strURL, False
    pXmlHttp.SetRequestHeader "Content-Type", "application/x-www-form-urlencoded"
    pXmlHttp.Send (strPostData)


     AzquoPost1 = pXmlHttp.responseText
     If Left(AzquoPost1, 6) = "error:" Then
        MsgBox (AzquoPost1)
        End
     End If

End Function


Function AzquoPostMulti(strURL As String, strPostData, strFileName As String)
    Const MULTIPART_BOUNDARY = "9876543210----------0123456789"

    Dim strResponse, xmlhttp, xmlDom, returnVal
    Dim strData

    strData = ""

    strData = strData & "--" & MULTIPART_BOUNDARY & vbCrLf
    strData = strData & "Content-Disposition: form-data; name=""parameters""" & vbCrLf & vbCrLf
    strData = strData & strPostData & "&connectionid=" & azConnectionId
    strData = strData & vbCrLf

     strData = strData & "--" & MULTIPART_BOUNDARY & vbCrLf
    strData = strData & "Content-Disposition: form-data; name=""uploadfile""" & vbCrLf
     strData = strData & "Content-Transfer-Encoding: binary" & vbCrLf & vbCrLf
    If Right(strFileName, 4) = ".xls" Or Right(strFileName, 4) = ".zip" Then
       strData = strData & EncodeBase64(ReadFile(strFileName))
    Else
       strData = strData & ReadFile(strFileName)
    End If
    strData = strData & vbCrLf
    
    
   strData = strData & "--" & MULTIPART_BOUNDARY & "--"

    Set xmlhttp = CreateObject("Microsoft.XMLHTTP")
    xmlhttp.Open "POST", "https://data.azquo.com:8443/api/Import", False

    xmlhttp.SetRequestHeader "Accept", "application/x-www-form-urlencoded,text/xml,application/xml,application/xhtml+xml,text/html;q=0.9,text/plain;q=0.8,image/png,*/*;q=0.5"
    xmlhttp.SetRequestHeader "Accept-Language", "en-us,en;q=0.5"
    xmlhttp.SetRequestHeader "Accept-Charset", "ISO-8859-1,utf-8;q=0.7,*;q=0.7"
    xmlhttp.SetRequestHeader "Content-Type", "multipart/form-data; boundary=" & MULTIPART_BOUNDARY
    xmlhttp.SetRequestHeader "Content-Length", Len(strData) + 2
    xmlhttp.Send strData

    AzquoPostMulti = xmlhttp.responseText
     If Left(AzquoPostMulti, 6) = "error:" Then
        MsgBox (AzquoPostMulti)
        End
     End If

End Function


Function EncodeBase64(sString As String) As String




Const clOneMask = 16515072          '000000 111111 111111 111111
Const clTwoMask = 258048            '111111 000000 111111 111111
Const clThreeMask = 4032            '111111 111111 000000 111111
Const clFourMask = 63               '111111 111111 111111 000000

Const clHighMask = 16711680         '11111111 00000000 00000000
Const clMidMask = 65280             '00000000 11111111 00000000
Const clLowMask = 255               '00000000 00000000 11111111

Const cl2Exp18 = 262144             '2 to the 18th power
Const cl2Exp12 = 4096               '2 to the 12th
Const cl2Exp6 = 64                  '2 to the 6th
Const cl2Exp8 = 256                 '2 to the 8th
Const cl2Exp16 = 65536              '2 to the 16th


    Dim bTrans(63) As Byte, lPowers8(255) As Long, lPowers16(255) As Long, bOut() As Byte, bIn() As Byte
    Dim lChar As Long, lTrip As Long, iPad As Integer, lLen As Long, lTemp As Long, lPos As Long, lOutSize As Long

    For lTemp = 0 To 63                                 'Fill the translation table.
        Select Case lTemp
            Case 0 To 25
                bTrans(lTemp) = 65 + lTemp              'A - Z
            Case 26 To 51
                bTrans(lTemp) = 71 + lTemp              'a - z
            Case 52 To 61
                bTrans(lTemp) = lTemp - 4               '1 - 0
            Case 62
                bTrans(lTemp) = 43                      'Chr(43) = "+"
            Case 63
                bTrans(lTemp) = 47                      'Chr(47) = "/"
        End Select
    Next lTemp

    For lTemp = 0 To 255                                'Fill the 2^8 and 2^16 lookup tables.
        lPowers8(lTemp) = lTemp * cl2Exp8
        lPowers16(lTemp) = lTemp * cl2Exp16
    Next lTemp

    iPad = Len(sString) Mod 3                           'See if the length is divisible by 3
    If iPad Then                                        'If not, figure out the end pad and resize the input.
        iPad = 3 - iPad
        sString = sString & String(iPad, Chr(0))
    End If

    bIn = StrConv(sString, vbFromUnicode)               'Load the input string.
    lLen = ((UBound(bIn) + 1) \ 3) * 4                  'Length of resulting string.
    lTemp = lLen \ 72                                   'Added space for vbCrLfs.
    lOutSize = ((lTemp * 2) + lLen) - 1                 'Calculate the size of the output buffer.
    ReDim bOut(lOutSize)                                'Make the output buffer.

    lLen = 0                                            'Reusing this one, so reset it.

    For lChar = LBound(bIn) To UBound(bIn) Step 3
        lTrip = lPowers16(bIn(lChar)) + lPowers8(bIn(lChar + 1)) + bIn(lChar + 2)    'Combine the 3 bytes
        lTemp = lTrip And clOneMask                     'Mask for the first 6 bits
        bOut(lPos) = bTrans(lTemp \ cl2Exp18)           'Shift it down to the low 6 bits and get the value
        lTemp = lTrip And clTwoMask                     'Mask for the second set.
        bOut(lPos + 1) = bTrans(lTemp \ cl2Exp12)       'Shift it down and translate.
        lTemp = lTrip And clThreeMask                   'Mask for the third set.
        bOut(lPos + 2) = bTrans(lTemp \ cl2Exp6)        'Shift it down and translate.
        bOut(lPos + 3) = bTrans(lTrip And clFourMask)   'Mask for the low set.
        If lLen = 68 Then                               'Ready for a newline
            bOut(lPos + 4) = 13                         'Chr(13) = vbCr
            bOut(lPos + 5) = 10                         'Chr(10) = vbLf
            lLen = 0                                    'Reset the counter
            lPos = lPos + 6
        Else
            lLen = lLen + 4
            lPos = lPos + 4
        End If
    Next lChar

    If bOut(lOutSize) = 10 Then lOutSize = lOutSize - 2 'Shift the padding chars down if it ends with CrLf.

    If iPad = 1 Then                                    'Add the padding chars if any.
        bOut(lOutSize) = 61                             'Chr(61) = "="
    ElseIf iPad = 2 Then
        bOut(lOutSize) = 61
        bOut(lOutSize - 1) = 61
    End If

    EncodeBase64 = StrConv(bOut, vbUnicode)                 'Convert back to a string and return it.

End Function



Public Function URLEncode(ByVal StringVal As String) As String

  Dim StringLen As Long: StringLen = Len(StringVal)

  If StringLen > 0 Then
    ReDim result(StringLen) As String
    Dim i As Long, CharCode As Integer
    Dim Char As String, Space As String

    Space = "%20"

    For i = 1 To StringLen
      Char = Mid$(StringVal, i, 1)
      CharCode = Asc(Char)
      Select Case CharCode
        Case 97 To 122, 65 To 90, 48 To 57, 45, 46, 95, 126
          result(i) = Char
        Case 32
          result(i) = Space
        Case 0 To 15
          result(i) = "%0" & Hex(CharCode)
        Case Else
          result(i) = "%" & Hex(CharCode)
      End Select
    Next i
    URLEncode = Join(result, "")
  End If
End Function



Sub LoadData()
'CheckConnectionId
  If (azConnectionId = "aborted") Then
     Exit Sub
  End If
  Application.ScreenUpdating = False
  
  ClearData
  azError = FillRowHeadings()
  If azError = "" Then
     azError = FillColumnHeadings()
  End If
  If azError = "" Then
     azError = FillData()
     If azError = "" Then
       HideRows
       TrimRowHeadings
       TrimColumnHeadings
     End If
  End If
  Application.ScreenUpdating = True
End Sub

Sub HideRows()
   Dim DataRegion As Range
   Dim TotalCol, RowNo, VisibleRow As Integer
   
   Set DataRegion = Range("az_DataRegion")
   TotalCol = DataRegion.Column + DataRegion.Columns.Count
   VisibleRow = 0
   For RowNo = DataRegion.Row To DataRegion.Row + DataRegion.rows.Count
      
      If Cells(RowNo, TotalCol) = 0 Then
         rows(RowNo).EntireRow.Hidden = True
      End If
   Next
 
End Sub

Function TrimRowHeadings()
   Dim RowHeadings As Range
   Dim RowNo, ColNo, VisibleRow As Integer
   
   Set RowHeadings = Range("az_DisplayRowHeadings")
   For ColNo = RowHeadings.Column To RowHeadings.Column + RowHeadings.Columns.Count - 1
      VisibleRow = RowHeadings.Row - 1
      For RowNo = RowHeadings.Row To RowHeadings.Row + RowHeadings.rows.Count - 1
         If rows(RowNo).EntireRow.Hidden = False Then
            If Cells(RowNo, ColNo) = Cells(VisibleRow, ColNo) Then
              Cells(RowNo, ColNo).ClearContents
            Else
               VisibleRow = RowNo
            End If
            
         End If
      Next
    Next
    
         


End Function


Function TrimColumnHeadings()
   Dim ColumnHeadings As Range
   Dim RowNo, ColNo, VisibleColumn As Integer
   Set ColumnHeadings = Range("az_DisplayColumnHeadings")
   For RowNo = ColumnHeadings.Row To ColumnHeadings.Row + ColumnHeadings.rows.Count - 1
      VisibleColumn = ColumnHeadings.Column - 1
      For ColNo = ColumnHeadings.Column To ColumnHeadings.Column + ColumnHeadings.Columns.Count - 1
            If Cells(RowNo, ColNo) = Cells(RowNo, VisibleColumn) Then
              Cells(RowNo, ColNo).ClearContents
            Else
               VisibleColumn = ColNo
            End If
      Next
    Next
    
         


End Function





Function FillRowHeadings()
     
     Dim thisSheet As Worksheet
     Dim DisplayRows As Range
     
     CheckRange ("az_RowHeadings")
     CheckRange ("az_DisplayRowHeadings")
     
     azResponse = AzquoPost("Value", "rowheadings=" & RangeText("az_RowHeadings"))
     CopyDataRegionToClipboard
     Set thisSheet = ActiveSheet
    
     Call FillTheRange(azResponse, "az_DisplayRowHeadings")
     Set DisplayRows = Range("az_DisplayRowHeadings")
     Dim MyData As DataObject

     Set MyData = New DataObject
     MyData.SetText azResponse
     MyData.PutInClipboard
     
     DisplayRows.Select
     Application.DisplayAlerts = False
     ActiveSheet.PasteSpecial Format:="Text"
     CopyClipboardBack
     Application.DisplayAlerts = True
     FillRowHeadings = ""
     rows(DisplayRows.Row & ":" & DisplayRows.Row + DisplayRows.rows.Count - 1).EntireRow.AutoFit
End Function

Function FillTheRange(DataIn, RegionName)

     Dim DataSent As Variant
     Dim NoRows, LastRow, FirstInsert, LastInsert, FirstRemove, LastRemove, RowCount As Integer
     Dim DisplayRows As Range
     
     DataSent = Split(DataIn, Strings.Chr(10))
     NoRows = UBound(DataSent) + 1
     Set DisplayRows = Range(RegionName)
     LastRow = DisplayRows.Row + DisplayRows.rows.Count - 1
     If DisplayRows.rows.Count < NoRows Then
         FirstInsert = LastRow
         LastInsert = DisplayRows.Row + NoRows - 2
         rows(FirstInsert & ":" & LastInsert).Insert
         rows(LastRow - 1).Copy
         rows(LastRow & ":" & LastInsert).PasteSpecial
     End If
     RowCount = DisplayRows.rows.Count
     If (NoRows > 1 And RowCount = 1) Then
       RowCount = 2
     End If
     If RowCount > NoRows Then
         LastRemove = DisplayRows.Row + RowCount - 2
         FirstRemove = DisplayRows.Row + NoRows - 1
         rows(FirstRemove & ":" & LastRemove).Delete
     End If
     Dim MyData As DataObject

     Set MyData = New DataObject
     MyData.SetText DataIn
     MyData.PutInClipboard
     
     DisplayRows.Select
     Application.DisplayAlerts = False
     ActiveSheet.PasteSpecial Format:="Text"

     
     
End Function

Function FillColumnHeadings()
     Dim NoColumns, ColCount, FirstInsert, LastInsert, FirstRemove, LastRemove As Integer
     Dim DIsplayColumns As Range
    
     CheckRange ("az_ColumnHeadings")
     CheckRange ("az_DisplayColumnHeadings")
     azResponse = AzquoPost("Value", "columnheadings=" & RangeText("az_ColumnHeadings"))
      CopyDataRegionToClipboard
     Dim ColumnHeadings As Variant
     If InStr(azResponse, Chr(10)) > 0 Then
        ColumnHeadings = Split(Left(azResponse, InStr(azResponse, Chr(10))), Chr(9))
     Else
         ColumnHeadings = Split(azResponse, Strings.Chr(9))
     End If
     NoColumns = UBound(ColumnHeadings) + 1
     Set DIsplayColumns = Range("az_DisplayColumnHeadings")
     If DIsplayColumns.Columns.Count < NoColumns Then
         FirstInsert = DIsplayColumns.Column + DIsplayColumns.Columns.Count - 1
         LastInsert = DIsplayColumns.Column + NoColumns - 2
         Range(Columns(FirstInsert), Columns(LastInsert)).Insert
     End If
     ColCount = DIsplayColumns.Columns.Count
     If ColCount = 1 And NoColumns > 1 Then
        ColCount = 2
     End If
     If ColCount > NoColumns Then
         LastRemove = DIsplayColumns.Column + ColCount - 2
         FirstRemove = DIsplayColumns.Column + NoColumns - 1
         Range(Columns(FirstRemove), Columns(LastRemove)).Delete
     End If
     Dim MyData As DataObject

     Set MyData = New DataObject
     MyData.SetText azResponse
     MyData.PutInClipboard
     
     DIsplayColumns.Select
     Application.DisplayAlerts = False
     ActiveSheet.PasteSpecial Format:="Text"
     CopyClipboardBack
     Application.DisplayAlerts = True
     FillColumnHeadings = ""
     rows(DIsplayColumns.Row & ":" & DIsplayColumns.Row + DIsplayColumns.rows.Count - 1).EntireRow.AutoFit
     

End Function

Function FillData()
    Dim Context, DataRegion As Range
 
     CheckRange ("az_Context")
     CheckRange ("az_DataRegion")
     Context = RangeText("az_Context")
     azResponse = AzquoPost("Value", "context=" & Context)
      
     ' copy the formulae in the data region to another sheet, so as to copy them back after the data region has been filled
     CopyDataRegionToClipboard
     Set DataRegion = Range("az_DataRegion")
     DataRegion.Select
     
     ' insert the data
     
     Dim MyData As DataObject
     Set MyData = New DataObject
     MyData.SetText azResponse
     MyData.PutInClipboard
     ActiveSheet.PasteSpecial Format:="Text"
     
     'now copy back the formulae and formats
     CopyClipboardBack
     FillData = ""
     Cells(1, 1).Select

End Function

Function CopyDataRegionToClipboard()
   Dim thisSheet, CopySheet As Worksheet
   Dim DataRegion As Range

    Set thisSheet = ActiveSheet
    AddSheet ("Azquo Clipboard")
     Set CopySheet = Sheets("Azquo Clipboard")
     Set DataRegion = Range("az_DataRegion")
     DataRegion.Copy
     ActiveSheet.Cells(1, 1).Select
     ActiveSheet.Paste
      ActiveWorkbook.Names.Add Name:="copyregion", RefersToR1C1:= _
        "='Azquo Clipboard'!R1C1:R" & DataRegion.rows.Count & "C" & DataRegion.Columns.Count
        
     thisSheet.Activate
End Function

Function CopyClipboardBack()
    Dim thisSheet, CopySheet As Worksheet
    
    Set thisSheet = ActiveSheet
    Set CopySheet = Sheets("Azquo Clipboard")
    CopySheet.Activate
     Range("copyregion").Copy
     thisSheet.Activate
     
     Range("az_DataRegion").Select
     Selection.PasteSpecial Paste:=xlPasteFormulas, SkipBlanks:=True
     Selection.PasteSpecial Paste:=xlPasteFormats
     ZapSheet ("Azquo clipboard")

End Function





Function RangeText(RangeName)
     Dim azRange As Range
     Dim RowNo, ColNo As Integer
     
     Set azRange = Range(RangeName)
     RangeText = ""
     For RowNo = 0 To azRange.rows.Count - 1
        If (RowNo > 0) Then
           RangeText = RangeText + Chr(10)
        End If
        For ColNo = 0 To azRange.Columns.Count - 1
           If (ColNo > 0) Then
              RangeText = RangeText + Chr(9)
           End If
           RangeText = RangeText + Cells(azRange.Row + RowNo, azRange.Column + ColNo).value
        Next
     Next
     RangeText = URLEncode(RangeText)
End Function


Function SaveData()
'     azResponse = AzquoPost("Value", "editeddata=" & RangeToStringArray(Range("az_DataRegion")))
     Thing = GetTextFromRangeText(Range("az_DataRegion"))
     azResponse = AzquoPost("Value", "editeddata=" & Thing)



End Function

Sub ClearData()
     
     Dim DataRegion As Range
     Dim FirstRemove As Integer
     Dim LastRemove As Integer
     
     Set DataRegion = Range("az_DataRegion")
     DataRegion.EntireRow.Hidden = False
     If DataRegion.rows.Count > 3 Then
         LastRemove = DataRegion.Row + DataRegion.rows.Count - 2
         FirstRemove = DataRegion.Row + 2
         rows(FirstRemove & ":" & LastRemove).Delete
     End If
     If DataRegion.Columns.Count > 3 Then
         LastRemove = DataRegion.Column + DataRegion.Columns.Count - 2
         FirstRemove = DataRegion.Column + 2
         Range(Columns(FirstRemove), Columns(LastRemove)).Delete
     End If
     ' copy the formulae
     Set DataRegion = Range("az_DataRegion")
      
     DataRegion.Copy
     DataRegion.ClearContents
     Range("az_DisplayRowHeadings").ClearContents
     Range("az_DisplayColumnHeadings").ClearContents
  
End Sub


Sub SearchData()
  'CheckConnectionId
  Dim azSearchName As String
  azSearchName = Range("az_SearchName")
   azResponse = AzquoPost("Name", """operation"":""structure"",""name"":""" & azSearchName & """")
   If (azResponse > "") Then
      azSearchNames.UserForm_Initialize
      azSearchNames.Show
   End If
   Range("az_currentSearch") = azNameChosen
   If azNameChosen > "" Then
      azResponse = AzquoPost("Value", "searchbynames=" & URLEncode(azNameChosen))
      Call FillTheRange(azResponse, "azSearchData")
   End If
   
   
End Sub




' off the internet and modified
Function GetTextFromRangeText(ByVal poRange As Range) As String
    Dim vRange As Variant
    Dim sRet As String
    Dim i As Integer
    Dim j As Integer

    If Not poRange Is Nothing Then

        vRange = poRange
        
        firstline = True
        For i = LBound(vRange) To UBound(vRange)
            firstcell = True
            For j = LBound(vRange, 2) To UBound(vRange, 2)
            If firstcell Then
                If firstline Then
                    sRet = vRange(i, j)
                Else
                    sRet = sRet & vbCrLf & vRange(i, j)
                End If
            Else
                sRet = sRet & vbTab & vRange(i, j)
            End If
                firstcell = False
            Next j
            firstline = False
        Next i
    End If

    GetTextFromRangeText = sRet
End Function


Sub register()
    Dim Name, Email, emailConfirmation, Business, Address1, Address2, Address3, Address4 As String
    Dim Postcode, Telephone, Password, passwordConfirmation, Keysent, Parameters, result As String
    
    Name = Range("signonName")
    Email = Range("signonEmail")
    emailConfirmation = Range("signonEmailConfirm")
    Business = Range("signonBusiness")
    Address1 = Range("signonAddress1")
    Address2 = Range("signonAddress2")
    Address3 = Range("signonAddress3")
    Address4 = Range("signonAddress4")
    Postcode = Range("signonPostcode")
    Telephone = Range("signonTelephone")
    Password = Range("signonPassword")
    passwordConfirmation = Range("signonPasswordConfirm")
    Keysent = Range("signonKey")
    
    If Name = "" Then
       MsgBox ("Please enter your name")
       End
    End If
    If Email = "" Then
       MsgBox ("Please enter a valid email")
       End
    End If
    If Email <> emailConfirmation Then
       MsgBox ("Your email addresses do not match")
       End
    End If
    If (Address1 = "") Then
       MsgBox ("Please enter the top line of your address")
       End
    End If
    If Postcode = "" Then
       MsgBox ("Please enter your postcode")
       End
    End If
    If Password = "" Then
       MsgBox ("Please enter a password")
       End
    End If
    If Password <> passwordConfirmation Then
       MsgBox ("Your passwords do not match")
       End
    End If
    Parameters = "op=signon&username=" & URLEncode(Name) & _
                 "&email=" & URLEncode(Email) & _
                 "&businessname=" & URLEncode(Business) & _
                 "&address1=" & URLEncode(Address1) & _
                 "&address2=" & URLEncode(Address2) & _
                 "&address3=" & URLEncode(Address3) & _
                 "&address4=" & URLEncode(Address4) & _
                 "&postcode=" & URLEncode(Postcode) & _
                 "&password=" & URLEncode(Password) & _
                 "&telephone=" & URLEncode(Telephone) & _
                 "&key=" & URLEncode(Keysent)
    result = AzquoPost1("Maintain", Parameters)
    If Keysent = "" Then
       Range("signonKey").EntireRow.Hidden = False
    Else
       Range("signonThankyou").EntireRow.Hidden = False
       azConnectionId = result
       'ZapSheet ("Introduction")
    End If
    

End Sub


Function CreateJson(Listname As String, RangeName As String)
    Dim JSON As String
    Dim Start As Range
    Dim RowNo, ColNo As Integer
    
    JSON = "{""" & Listname & """:["
    Set Start = Range(RangeName)
    RowNo = 1
    While Start.Offset(RowNo, 1) > 0
       JSON = JSON & "{"
       If RowNo > 1 Then
          JSON = JSON & ","
       End If
       ColNo = 0
       While Start.Offset(0, ColNo) > ""
          If ColNo > 0 Then
             JSON = JSON & ","
          End If
          JSON = JSON & """" & Start.Offset(0, ColNo) & """:""" & Start.Offset(RowNo, ColNo) & """"
          ColNo = ColNo + 1
       Wend
       RowNo = RowNo + 1
       JSON = JSON & "}"
     Wend
     CreateJson = JSON
    
End Function


Sub FillJSONRange(RangeName As String, response As String)
  Dim lib As New JSONLib
  Dim JSON As Object
  Dim oneline As Object
  Dim Count As Integer
  Dim RowNo, ColNo As Integer
  
  RowNo = Range(RangeName).Row
  Count = 1
  While Cells(RowNo + Count, 1) > ""
    Count = Count + 1
  Wend
  rows((RowNo + 1) & ":" & (RowNo + Count)).EntireRow.ClearContents
  Count = 0
  Set JSON = lib.parse(response)
  For Each oneline In JSON
     Count = Count + 1
    rows(RowNo + 1).EntireRow.Copy
     rows(RowNo + Count).EntireRow.Insert
     rows(RowNo + Count).EntireRow.PasteSpecial
     ColNo = 1
     While Cells(RowNo, ColNo) > ""
       Cells(RowNo + Count, ColNo) = oneline(Cells(RowNo, ColNo).value)
       ColNo = ColNo + 1
     Wend
     
  Next
  Application.CutCopyMode = False
  
  Cells(RowNo + 1, 1).Select
End Sub

Sub ActivateUsers()
  Sheets("User maintenance").Activate

End Sub

Sub ActivateDatabases()
   Sheets("Database maintenance").Activate
End Sub

Sub ActivateAccess()
   Sheets("Access maintenance").Activate
End Sub

Sub ActivateUpload()
   Sheets("Upload files").Activate
End Sub
Sub DownloadDatabaseList()
   Dim result As String
   result = AzquoPost("Maintain", "op=databaselist")
   Call FillJSONRange("azDatabaseHeadings", result)
End Sub

Sub DownloadPermissionsList()
   Dim result As String
   result = AzquoPost("Maintain", "op=accesslist")
   Call FillJSONRange("azPermissionsHeadings", result)
End Sub

Sub DownloadUserList()
   Dim result As String
   result = AzquoPost("Maintain", "op=userlist")
   Call FillJSONRange("azUserHeadings", result)
End Sub


Sub DownloadLogonList()
   Dim result As String
   result = AzquoPost("Maintain", "op=logonlist")
   Call FillJSONRange("azLogonHeadings", result)
End Sub

Sub SavePermissions()
    azResponse = AzquoPost("Maintain", "op=savepermissions&json=" & CreateJson("permissions", "azPermissionsHeadings"))
     DownloadAccessList
End Sub

Sub SaveUsers()
     Dim Thing As String
     azResponse = AzquoPost("Maintain", "op=saveusers&json=" & CreateJson("users", "azUserHeadings"))
     DownloadUserList
End Sub

Sub NewDatabase()
  
   azResponse = AzquoPost("Maintain", "op=newdatabase&database=" & Range("az_NewDatabase"))
   DownloadDatabaseList
End Sub

Sub BackupDatabase()
  Dim SourceName, TargetName, SummaryLevel As String
  SourceName = Range("SourceDatabase")
  TargetName = Range("TargetDatabase")
  SummaryLevel = Range("SummaryLevel")
  azResponse = AzquoPost("Maintain", "op=backupdatabase&source=" & SourceName & " & target=" & TargetName & "&summarylevel=" & SummaryLevel)
  DownloadDatabaseList
End Sub

Sub TruncateDatabase()
 Dim DatabaseName, StartDate As String
  DatabaseName = Range("TruncateDatabase")
  StartDate = Format(StartDate, "DD/MM/YY")
  If !MsgBox("Are you sure that you want to truncate " & DatabaseName & " before " & StartDate) Then
    End
  End If
  azResponse = AzquoPost("Maintain", "op=truncatedatabase&database=" & DatabaseName & "&startdate=" & StartDate)
  DownloadDatabaseList
End Sub

Sub DeleteDatabase()
  Dim Database As String
  Database = Range("DeleteDatabase")
  If !MsgBox("Are you sure that you want to delete " & Database, YesNo) Then
     End
  End If
  azResponse = AzquoPost("Maintain", "op=deletedatabase&database=" & DatabaseName)
  DownloadDatabaseList
End Sub


Sub SelectUploadFile()
    Dim fileName As String
    fileName = Application.GetOpenFilename
    If (fileName > "") Then
       Range("az_UploadFilename") = fileName
    End If

End Sub

Function ReadFile(fileName As String) As String

  Dim f As Integer
  f = FreeFile()
  Open fileName For Binary Access Read As #f
    ReadFile = Space(FileLen(fileName))
    Get #f, , ReadFile
  Close #f
  
End Function

Sub UploadFiles()
Dim az_Parameters, az_response As String
Dim fileName As String
fileName = Range("az_UploadFilename")
While InStr(fileName, "\") > 0
    fileName = Mid(fileName, InStr(fileName, "\") + 1, 1000)
Wend


az_Parameters = "filename=" & fileName & _
                "&filetype=" & Range("az_UploadFiletype") & _
                "&language=" & Range("az_ImportLanguage") & _
                "&create=true"
  CheckConnectionId
  az_response = AzquoPostMulti("Import", az_Parameters, Range("az_UploadFilename"))
  DownloadUploadRecord
 
 End Sub
 
 Sub DownloadUploadRecord()
    Dim azResponse As String
    azResponse = AzquoPost("Maintain", "op=uploadslist")
    Call FillJSONRange("azUploadHeadings", azResponse)
 End Sub


Sub rtclkEdit()
  azSearchNames.mcEdit
End Sub

Sub rtclkCut()
  azSearchNames.mcCut
End Sub

Sub rtclkCopy()
  azSearchNames.mcCopy
End Sub

Sub rtclkPaste()
  azSearchNames.mcPaste
End Sub

Sub rtclkAddPeer()
 azSearchNames.cmdAddSibling_Click
End Sub

Sub rtclkAddChild()
 azSearchNames.cmdAddSibling_Click
End Sub

Sub rtclkDeleteName()
  azSearchNames.cmdRemoveNode_Click
End Sub
